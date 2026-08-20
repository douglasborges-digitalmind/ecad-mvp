<#
.SYNOPSIS
    Gera a planilha XLSX de eventos ECAD a partir do Control Center rodando localmente (modo cloud).

.DESCRIPTION
    Baixa a planilha gerada pelo endpoint GET /api/exportacao/planilha do
    Control Center rodando localmente via Docker (modo cloud do run-local.ps1).

    Diferente do modo Azure, aqui o Control Center esta em http://127.0.0.1:8080
    (ou porta configurada). O endpoint retorna um arquivo .xlsx que e salvo
    no diretorio de saida informado (default: ./exports).

    Pre-requisitos:
      - Stack local subida via: .\scripts\run-local.ps1 -Mode cloud
      - Control Center acessivel em http://127.0.0.1:8080 (ou -ControlCenterUrl)

.PARAMETER OutputDir
    Diretorio onde o arquivo .xlsx sera salvo (default: .\exports).
    Criado se nao existir.

.PARAMETER OutputName
    Nome do arquivo de saida (default: Planilha-Eventos-ECAD-<data>.xlsx).
    Use para sobrescrever o nome padrao.

.PARAMETER ControlCenterUrl
    URL base do Control Center (default: http://127.0.0.1:8080).
    Use para apontar para outra porta/host.

.PARAMETER TimeoutSec
    Timeout em segundos para o download (default: 300). A geracao da planilha
    pode demorar dependendo do volume de eventos no MongoDB local.

.PARAMETER DryRun
    Mostra o que seria executado (URL e destino) sem fazer o download.

.EXAMPLE
    # Gerar planilha do Control Center local (MongoDB/Azurite/Kafka via Docker)
    .\scripts\exportar-planilha-local.ps1

.EXAMPLE
    # Especificar diretorio e nome de saida
    .\scripts\exportar-planilha-local.ps1 -OutputDir D:\Planilhas -OutputName "Eventos-2026-08.xlsx"

.EXAMPLE
    # Apontar para Control Center em outra porta
    .\scripts\exportar-planilha-local.ps1 -ControlCenterUrl "http://127.0.0.1:8085"

.EXAMPLE
    # Simular execucao sem download
    .\scripts\exportar-planilha-local.ps1 -DryRun

.NOTES
    Endpoint: GET /api/exportacao/planilha
    Retorna:  application/vnd.openxmlformats-officedocument.spreadsheetml.sheet
    Headers:  Content-Disposition: attachment; filename="Planilha-Eventos-ECAD-<data>.xlsx"

    Para subir a stack local: .\scripts\run-local.ps1 -Mode cloud
#>
param(
    [string]$OutputDir,
    [string]$OutputName,
    [string]$ControlCenterUrl = "http://127.0.0.1:8080",
    [int]$TimeoutSec = 300,
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"
$root = (Resolve-Path "$PSScriptRoot\..").Path

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

$ccUrl = $ControlCenterUrl.TrimEnd('/')

# --- Health check antes de baixar ---
Write-Step "Verificando saude do Control Center..."
Write-Info "URL: $ccUrl"
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
    Write-Info "Suba a stack local primeiro: .\scripts\run-local.ps1 -Mode cloud"
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

$contentDisposition = $response.Headers['Content-Disposition']
if ($contentDisposition -and $contentDisposition -match 'filename="?([^";]+)"?') {
    Write-Info "Nome sugerido pelo servidor: $($matches[1])"
}

Write-Host "`n=== Concluido ===" -ForegroundColor Cyan
