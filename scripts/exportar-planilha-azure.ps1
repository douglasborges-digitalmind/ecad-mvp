<#
.SYNOPSIS
    Gera a planilha XLSX de eventos ECAD a partir do Control Center no Azure.

.DESCRIPTION
    Baixa a planilha gerada pelo endpoint GET /api/exportacao/planilha do
    Control Center rodando como Container App no Azure Container Apps.

    Descobre automaticamente o FQDN do Container App 'control-center-<suffix>'
    a partir de .deploy/azure-resources.json e `az containerapp show`.

    Requer:
      - Azure CLI (az) instalada e autenticada (az login)
      - Extensao containerapp instalada
      - Control Center deployado e saudavel no Azure

    O endpoint retorna um arquivo .xlsx (Content-Type:
    application/vnd.openxmlformats-officedocument.spreadsheetml.sheet) que e
    salvo no diretorio de saida informado (default: ./exports).

.PARAMETER OutputDir
    Diretorio onde o arquivo .xlsx sera salvo (default: .\exports).
    Criado se nao existir.

.PARAMETER OutputName
    Nome do arquivo de saida (default: Planilha-Eventos-ECAD-<data>.xlsx).
    Use para sobrescrever o nome padrao.

.PARAMETER ControlCenterUrl
    URL base do Control Center. Se omitido, descobre automaticamente via FQDN
    do Container App. Use para sobrescrever a descoberta automatica.

.PARAMETER Slot
    dev (usa azure_app_settings.dev.json) | prod (usa azure_app_settings.json).
    Apenas afeta a leitura do sufixo em .deploy/azure-resources.json.

.PARAMETER TimeoutSec
    Timeout em segundos para o download (default: 300). A geracao da planilha
    pode demorar dependendo do volume de eventos.

.PARAMETER DryRun
    Mostra o que seria executado (URL e destino) sem fazer o download.

.EXAMPLE
    # Gerar planilha no Azure (descoberta automatica de URL)
    .\scripts\exportar-planilha-azure.ps1

.EXAMPLE
    # Especificar diretorio e nome de saida
    .\scripts\exportar-planilha-azure.ps1 -OutputDir D:\Planilhas -OutputName "Eventos-2026-08.xlsx"

.EXAMPLE
    # Sobrescrever a URL do Control Center
    .\scripts\exportar-planilha-azure.ps1 -ControlCenterUrl "https://control-center-meu.azurecontainerapps.io"

.EXAMPLE
    # Simular execucao sem download
    .\scripts\exportar-planilha-azure.ps1 -DryRun

.NOTES
    Endpoint: GET /api/exportacao/planilha
    Retorna:  application/vnd.openxmlformats-officedocument.spreadsheetml.sheet
    Headers:  Content-Disposition: attachment; filename="Planilha-Eventos-ECAD-<data>.xlsx"
#>
param(
    [string]$OutputDir,
    [string]$OutputName,
    [string]$ControlCenterUrl,
    [ValidateSet("dev", "prod")]
    [string]$Slot = "dev",
    [int]$TimeoutSec = 300,
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"
$root = (Resolve-Path "$PSScriptRoot\..").Path
$resourcesFile = Join-Path $root ".deploy\azure-resources.json"

# --- Helpers de output ---
function Write-Step([string]$msg) { Write-Host "`n[STEP] $msg" -ForegroundColor Yellow }
function Write-Ok([string]$msg)   { Write-Host "  [OK] $msg" -ForegroundColor Green }
function Write-Warn([string]$msg) { Write-Host "  [!] $msg" -ForegroundColor Yellow }
function Write-Err([string]$msg)  { Write-Host "  [X] $msg" -ForegroundColor Red }
function Write-Info([string]$msg) { Write-Host "  $msg" -ForegroundColor Gray }

trap {
    Write-Err "Falha inesperada: $_"
    break
}

# --- Carrega configuracao de recursos Azure ---
if (-not (Test-Path $resourcesFile)) {
    Write-Err "Arquivo nao encontrado: $resourcesFile"
    Write-Info "Execute .\scripts\run-azure.ps1 -Mode infra para provisionar."
    exit 1
}
$cfg = Get-Content $resourcesFile -Raw | ConvertFrom-Json
$rg     = $cfg.resourceGroup
$suffix = $cfg.nameSuffix

# --- Valida pre-requisitos ---
Write-Step "Validando pre-requisitos..."
$missing = @()
if (-not (Get-Command az -ErrorAction SilentlyContinue)) { $missing += "Azure CLI (az)" }
if ($missing.Count -gt 0) {
    Write-Err "Pre-requisitos ausentes: $($missing -join ', ')"
    exit 1
}

$acct = az account show --query "name" -o tsv 2>$null
if ($LASTEXITCODE -ne 0) {
    Write-Err "Nao autenticado no Azure. Execute: az login"
    exit 1
}
Write-Ok "Autenticado na subscription: $acct"

# Garante extensao containerapp
$ext = az extension list --query "[?name=='containerapp'].name" -o tsv 2>$null
if (-not $ext) {
    Write-Host "  Instalando extensao containerapp..." -ForegroundColor Yellow
    az extension add --name containerapp --yes 2>$null | Out-Null
}
Write-Ok "Extensao containerapp OK"

# --- Descobre FQDN do Control Center ---
if (-not $ControlCenterUrl) {
    Write-Step "Descobrindo FQDN do Control Center..."
    $appName = "control-center-$suffix"
    $fqdn = az containerapp show --name $appName --resource-group $rg `
        --query "properties.configuration.ingress.fqdn" -o tsv 2>$null
    if (-not $fqdn) {
        Write-Err "Container App '$appName' nao encontrado ou sem FQDN."
        Write-Info "Verifique o deploy: .\scripts\run-azure.ps1 -Mode apps -Service control-center"
        exit 1
    }
    $ControlCenterUrl = "https://$fqdn"
    Write-Ok "FQDN: $fqdn"
}
$ccUrl = $ControlCenterUrl.TrimEnd('/')

# --- Health check antes de baixar ---
Write-Step "Verificando saude do Control Center..."
try {
    $health = Invoke-RestMethod -Uri "$ccUrl/api/health" -Method GET -TimeoutSec 30
    if ($health.status -eq "healthy") {
        Write-Ok "Control Center saudavel (component=$($health.component))"
    } else {
        Write-Warn "Control Center degradado (status=$($health.status))"
        Write-Info "Continuando mesmo assim..."
    }
} catch {
    Write-Err "Control Center indisponivel: $($_.Exception.Message)"
    Write-Info "Verifique: .\scripts\ecadexecute-azure.ps1 -Action logs -Service control-center"
    exit 1
}

# --- Prepara diretorio de saida ---
if (-not $OutputDir) { $OutputDir = Join-Path $root "exports" }
if (-not (Test-Path $OutputDir)) {
    Write-Info "Criando diretorio de saida: $OutputDir"
    New-Item -ItemType Directory -Path $OutputDir -Force | Out-Null
}

# --- Define nome do arquivo ---
if (-not $OutputName) {
    $dataHoje = (Get-Date).ToUniversalTime().ToString("yyyy-MM-dd")
    $OutputName = "Planilha-Eventos-ECAD-$dataHoje.xlsx"
}
$outputPath = Join-Path $OutputDir $OutputName

# --- Executa o download ---
$uri = "$ccUrl/api/exportacao/planilha"
Write-Step "Baixando planilha..."
Write-Info "URL:      $uri"
Write-Info "Destino:  $outputPath"
Write-Info "Timeout:  ${TimeoutSec}s"

if ($DryRun) {
    Write-Warn "[DRY-RUN] Nao executando o download."
    return
}

try {
    # Invoke-WebRequest preserva o stream binario do .xlsx
    $response = Invoke-WebRequest -Uri $uri -Method GET -TimeoutSec $TimeoutSec `
        -OutFile $outputPath -PassThru
} catch {
    Write-Err "Falha ao baixar a planilha: $($_.Exception.Message)"
    if ($_.Exception.Response) {
        $status = [int]$_.Exception.Response.StatusCode
        Write-Info "Status HTTP: $status"
    }
    exit 1
}

if (-not (Test-Path $outputPath)) {
    Write-Err "Arquivo nao foi salvo em $outputPath"
    exit 1
}

$tamanho = (Get-Item $outputPath).Length
$tamanhoKb = [math]::Round($tamanho / 1KB, 2)
Write-Ok "Planilha baixada com sucesso!"
Write-Info "Arquivo: $outputPath"
Write-Info "Tamanho: $tamanhoKb KB"
Write-Info "Content-Type: $($response.Headers['Content-Type'])"

# Tenta extrair o nome sugerido pelo Content-Disposition (caso queira conferir)
$contentDisposition = $response.Headers['Content-Disposition']
if ($contentDisposition) {
    # Headers podem vir como array; junta para string antes do match
    $cdStr = ($contentDisposition -join ', ')
    if ($cdStr -match 'filename="?([^";]+)"?') {
        Write-Info "Nome sugerido pelo servidor: $($matches[1])"
    } else {
        Write-Info "Content-Disposition: $cdStr"
    }
}

Write-Host "`n=== Concluido ===" -ForegroundColor Cyan
