<#
.SYNOPSIS
    Gera a planilha XLSX de eventos ECAD a partir de um arquivo JSON local (modo local simulado).

.DESCRIPTION
    Baixa a planilha gerada pelo endpoint GET /api/exportacao/planilha?localFile=<caminho>
    do Control Center rodando no host em modo local (LOCAL_DEVELOPMENT_ENABLED=true),
    usando o filesystem (LocalJsonFileStore) em vez de MongoDB/Azurite/Kafka.

    Cenario de uso: o Control Center foi iniciado via
        .\scripts\run-local.ps1 -Mode local -Service control-center
    e os eventos estao armazenados como JSON em
        <LOCAL_DEVELOPMENT_ROOT>\data\eventos.json
    (default: %TEMP%\ecad-localdev\data\eventos.json ou o caminho configurado).

    O parametro -LocalFile e repassado ao endpoint como query param `localFile`.
    Se omitido, o endpoint gera a planilha a partir do LocalEventoRepository
    (que le o eventos.json do LOCAL_DEVELOPMENT_ROOT). Se informado, o endpoint
    le o arquivo JSON especificado (lista de Evento) e gera a planilha a partir dele.

    O Control Center valida que:
      - LOCAL_DEVELOPMENT_ENABLED=true (caso contrario retorna 400)
      - O caminho esta dentro de LOCAL_DEVELOPMENT_ROOT ou do diretorio de trabalho
      - O arquivo existe (caso contrario retorna 404 -> 500)

.PARAMETER LocalFile
    Caminho (absoluto ou relativo a LOCAL_DEVELOPMENT_ROOT) de um arquivo JSON
    contendo uma lista de Evento. Se omitido, o endpoint usa o eventos.json
    padrao do LocalEventoRepository.

    Exemplo: .localdev\data\eventos.json
             C:\Users\voce\AppData\Local\Temp\ecad-localdev\data\eventos.json

.PARAMETER OutputDir
    Diretorio onde o arquivo .xlsx sera salvo (default: .\exports).
    Criado se nao existir.

.PARAMETER OutputName
    Nome do arquivo de saida (default: Planilha-Eventos-ECAD-<data>.xlsx).

.PARAMETER ControlCenterUrl
    URL base do Control Center (default: http://127.0.0.1:8080).

.PARAMETER TimeoutSec
    Timeout em segundos para o download (default: 300).

.PARAMETER DryRun
    Mostra o que seria executado (URL e destino) sem fazer o download.

.EXAMPLE
    # Gerar planilha a partir do eventos.json padrao do LocalEventoRepository
    .\scripts\exportar-planilha-local-simulado.ps1

.EXAMPLE
    # Gerar planilha a partir de um arquivo JSON especifico
    .\scripts\exportar-planilha-local-simulado.ps1 -LocalFile ".localdev\data\eventos.json"

.EXAMPLE
    # Caminho absoluto (deve estar dentro do LOCAL_DEVELOPMENT_ROOT ou user.dir)
    .\scripts\exportar-planilha-local-simulado.ps1 -LocalFile "C:\Temp\ecad-localdev\data\eventos.json"

.EXAMPLE
    # Especificar diretorio e nome de saida
    .\scripts\exportar-planilha-local-simulado.ps1 -OutputDir D:\Planilhas -OutputName "Eventos-simulado.xlsx"

.EXAMPLE
    # Simular execucao sem download
    .\scripts\exportar-planilha-local-simulado.ps1 -LocalFile ".localdev\data\eventos.json" -DryRun

.NOTES
    Endpoint: GET /api/exportacao/planilha?localFile=<caminho>
    Retorna:  application/vnd.openxmlformats-officedocument.spreadsheetml.sheet
    Headers:  Content-Disposition: attachment; filename="Planilha-Eventos-ECAD-<data>.xlsx"

    Requer: Control Center rodando no host via
        .\scripts\run-local.ps1 -Mode local -Service control-center

    O Control Center em modo local usa LOCAL_DEVELOPMENT_ENABLED=true (default)
    e armazena dados em LOCAL_DEVELOPMENT_ROOT (default: %TEMP%\ecad-localdev).
#>
param(
    [string]$LocalFile,
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

    # Confirma que o modo local esta ativo (localDevelopment=true no /api/health)
    $localDev = $health.localDevelopment
    if ($localDev -eq $true) {
        Write-Ok "Modo local simulado ativo (LOCAL_DEVELOPMENT_ENABLED=true)"
    } elseif ($localDev -eq $false) {
        Write-Warn "Control Center NAO esta em modo local (localDevelopment=false)."
        Write-Info "O parametro -LocalFile pode ser rejeitado (HTTP 400)."
        Write-Info "Para subir em modo local: .\scripts\run-local.ps1 -Mode local -Service control-center"
    } else {
        Write-Info "Nao foi possivel confirmar o modo local via /api/health."
    }
} catch {
    Write-Err "Control Center indisponivel: $($_.Exception.Message)"
    Write-Info "Suba o Control Center em modo local primeiro:"
    Write-Info "  .\scripts\run-local.ps1 -Mode local -Service control-center"
    exit 1
}

# --- Valida o arquivo local (se informado) ---
if ($LocalFile) {
    Write-Step "Validando arquivo local..."
    Write-Info "LocalFile informado: $LocalFile"

    # Tenta resolver como caminho absoluto para validar existencia local.
    # Nota: o Control Center tambem aceita caminhos relativos ao LOCAL_DEVELOPMENT_ROOT,
    # portanto a ausencia do arquivo no host nao significa que o endpoint falhara.
    $resolvedPath = $LocalFile
    if (-not [System.IO.Path]::IsPathRooted($resolvedPath)) {
        $resolvedPath = (Resolve-Path -Path $LocalFile -ErrorAction SilentlyContinue).Path
    }
    if ($resolvedPath -and (Test-Path $resolvedPath)) {
        $tamanho = (Get-Item $resolvedPath).Length
        $tamanhoKb = [math]::Round($tamanho / 1KB, 2)
        Write-Ok "Arquivo encontrado no host: $resolvedPath ($tamanhoKb KB)"
    } else {
        Write-Warn "Arquivo nao encontrado no host: $LocalFile"
        Write-Info "Se for relativo ao LOCAL_DEVELOPMENT_ROOT, o Control Center pode resolve-lo."
    }
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
    $sufixo = if ($LocalFile) { "-simulado" } else { "" }
    $OutputName = "Planilha-Eventos-ECAD-$dataHoje$sufixo.xlsx"
}
$outputPath = Join-Path $OutputDir $OutputName

# --- Monta a URI com query param localFile (se informado) ---
$uri = "$ccUrl/api/exportacao/planilha"
if ($LocalFile) {
    # Uri.EscapeDataString preserva barras e caracteres especiais do caminho
    $encoded = [Uri]::EscapeDataString($LocalFile)
    $uri += "?localFile=$encoded"
}

# --- Executa o download ---
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
        if ($status -eq 400) {
            Write-Info "Possivel causa: LOCAL_DEVELOPMENT_ENABLED=false no Control Center."
            Write-Info "Suba em modo local: .\scripts\run-local.ps1 -Mode local -Service control-center"
        } elseif ($status -eq 404 -or $status -eq 500) {
            Write-Info "Possivel causa: arquivo nao encontrado no LOCAL_DEVELOPMENT_ROOT."
            Write-Info "Verifique o caminho informado em -LocalFile."
        }
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
