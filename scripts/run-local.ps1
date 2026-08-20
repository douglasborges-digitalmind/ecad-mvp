<#
.SYNOPSIS
    Inicia a stack ECAD Java localmente com infraestrutura open-source.
.DESCRIPTION
    Modos: local (filesystem), cloud (MongoDB/Azurite/Kafka locais via Docker), infra (apenas infraestrutura)
.PARAMETER Mode
    local, cloud, infra
.PARAMETER Service
    Nome do servico especifico
.EXAMPLE
    .\scripts\run-local.ps1 -Mode infra
    .\scripts\run-local.ps1 -Mode cloud
    .\scripts\run-local.ps1 -Mode cloud -Service processing-engine
.NOTES
    Infra: MongoDB 7.0, Azurite 3.32.0 (--skipApiVersionCheck), Kafka 3.7.0 (KRaft)
    Rede dinamica via COMPOSE_PROJECT_NAME. Env via x-common-env no docker-compose.yml.
#>
param(
    [Parameter(Position=0)]
    [ValidateSet("local","cloud","infra")]
    [string]$Mode = "local",
    [string]$Service
)
$ErrorActionPreference = "Stop"
$root = (Resolve-Path "$PSScriptRoot\..").Path
$composeFile = "$root\docker-compose.yml"
$envFile = Join-Path $root ".env"
$projectName = if ($env:COMPOSE_PROJECT_NAME) { $env:COMPOSE_PROJECT_NAME } else { "ecad" }
$networkName = "${projectName}-network"
$healthCheckTimeoutSec = 120
$healthCheckIntervalSec = 2

# Em caso de falha critica, mostra o estado atual dos containers para facilitar
# o diagnostico e sugere o comando de limpeza. (S16)
function Invoke-FailureCleanup {
    Write-Host "`n[!] Falha durante a execucao. Estado atual dos containers:" -ForegroundColor Red
    $composeArgs = @('-f', $composeFile)
    if (Test-Path $envFile) { $composeArgs += '--env-file', $envFile }
    docker compose @composeArgs ps 2>$null
    Write-Host "`nPara limpar tudo: docker compose -f $composeFile down -v" -ForegroundColor Yellow
}
$null = Register-EngineEvent -SourceIdentifier PowerShell.Exiting -Action { } -SupportEvent
trap {
    Invoke-FailureCleanup
    break
}

function Write-Step([string]$msg) { Write-Host "`n[STEP] $msg" -ForegroundColor Yellow }
function Write-Ok([string]$msg) { Write-Host "  [OK] $msg" -ForegroundColor Green }
function Write-Warn([string]$msg) { Write-Host "  [!] $msg" -ForegroundColor Yellow }
function Write-Err([string]$msg) { Write-Host "  [X] $msg" -ForegroundColor Red }

function Test-Prerequisites {
    Write-Step "Validando pre-requisitos..."
    $missing = @()
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { $missing += "Docker" }
    try { docker compose version --short | Out-Null } catch { $missing += "Docker Compose v2" }
    if (-not (Test-Path "$root\mvndw.ps1")) { $missing += "mvndw.ps1 (Maven wrapper)" }
    if ($missing.Count -gt 0) { Write-Err "Pre-requisitos ausentes: $($missing -join ', ')"; exit 1 }
    try { docker version --format '{{.Server.Version}}' | Out-Null } catch { Write-Err "Docker daemon nao esta rodando."; exit 1 }
    Write-Ok "Docker, Compose e Maven wrapper OK"
}

function Start-Infrastructure {
    Write-Step "Subindo infraestrutura (MongoDB, Azurite, Kafka)..."
    $composeArgs = @('-f', $composeFile, '--profile', 'infra')
    if (Test-Path $envFile) { $composeArgs += '--env-file', $envFile }
    docker compose @composeArgs up -d
    if ($LASTEXITCODE -ne 0) { Write-Err "Falha ao subir infraestrutura"; exit 1 }
    Write-Host "Aguardando healthchecks..." -ForegroundColor Yellow
    $services = @('mongodb', 'azurite', 'kafka')
    foreach ($svc in $services) {
        Write-Host "  Aguardando $svc..." -NoNewline
        $healthy = $false
        $containerName = "${projectName}-${svc}-1"
        for ($i = 0; $i -lt ($healthCheckTimeoutSec / $healthCheckIntervalSec); $i++) {
            $status = docker inspect --format='{{.State.Health.Status}}' $containerName 2>$null
            if ($status -eq "healthy") { $healthy = $true; break }
            if ($status -eq "unhealthy") { Write-Err "$svc unhealthy"; exit 1 }
            Start-Sleep -Seconds $healthCheckIntervalSec
            Write-Host "." -NoNewline
        }
        Write-Host ""
        if (-not $healthy) { Write-Err "$svc nao ficou saudavel (timeout)"; exit 1 }
        Write-Ok "$svc saudavel"
    }
    Write-Host "Criando container 'captura-documentos' no Azurite..." -ForegroundColor Yellow
    $connStr = "DefaultEndpointsProtocol=http;AccountName=devstoreaccount1;AccountKey=Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==;BlobEndpoint=http://azurite:10000/devstoreaccount1;"
    # docker run puxa a imagem automaticamente se ainda nao existir localmente.
    $result = docker run --rm --network $networkName mcr.microsoft.com/azure-cli az storage container create --name captura-documentos --connection-string "$connStr" 2>&1
    if ($LASTEXITCODE -eq 0) { Write-Ok "Container 'captura-documentos' criado" }
    elseif ($result -match "ContainerAlreadyExists|already exists|400|409") { Write-Ok "Container ja existe" }
    else { Write-Warn "Aviso (app trata): $result" }
    Write-Host "Criando topicos Kafka..." -ForegroundColor Yellow
    $kafkaContainer = "${projectName}-kafka-1"
    # Apenas topicos de aplicacao. O topico interno __consumer_offsets e gerado
    # automaticamente pelo Kafka quando o primeiro consumer faz commit de offset.
    $topics = @(
        @{ Name = "scraping_commands"; Partitions = 3 },
        @{ Name = "captured_documents"; Partitions = 3 }
    )
    foreach ($topic in $topics) {
        $output = docker exec $kafkaContainer /opt/kafka/bin/kafka-topics.sh --create --if-not-exists --bootstrap-server localhost:9092 --topic $($topic.Name) --partitions $($topic.Partitions) --replication-factor 1 2>&1
        if ($LASTEXITCODE -eq 0) {
            Write-Ok "Topico '$($topic.Name)' criado"
        } else {
            $existing = docker exec $kafkaContainer /opt/kafka/bin/kafka-topics.sh --list --bootstrap-server localhost:9092 2>$null | Select-String $topic.Name
            if ($existing) {
                Write-Ok "Topico '$($topic.Name)' ja existe"
            } else {
                Write-Warn "Aviso ao criar topico '$($topic.Name)': $output"
            }
        }
    }
    Write-Host "Topicos atuais:" -ForegroundColor Gray
    docker exec $kafkaContainer /opt/kafka/bin/kafka-topics.sh --list --bootstrap-server localhost:9092 2>$null | ForEach-Object { Write-Host "  $_" -ForegroundColor DarkGray }
}

function Build-Project {
    Write-Step "Compilando projeto..."
    & "$root\mvndw.ps1" install -DskipTests -q
    if ($LASTEXITCODE -ne 0) { Write-Err "Falha na compilacao"; exit 1 }
    Write-Ok "Build OK"
}

function Run-Services {
    Write-Step "Iniciando servicos..."
    $composeArgs = @('-f', $composeFile, '--profile', 'full')
    if (Test-Path $envFile) { $composeArgs += '--env-file', $envFile }
    if ($Service) {
        docker compose @composeArgs up --build -d $Service
        if ($LASTEXITCODE -ne 0) { Write-Err "Falha ao iniciar $Service"; exit 1 }
        Write-Host "Servico '$Service' rodando em background." -ForegroundColor Cyan
        Write-Host "Logs: docker compose -f $composeFile --env-file $envFile logs -f $Service" -ForegroundColor Gray
    } else {
        docker compose @composeArgs up --build
    }
}

Test-Prerequisites
switch ($Mode) {
    "local" {
        Write-Host "Modo LOCAL: rodando servico no host..." -ForegroundColor Cyan
        if (-not $Service) {
            Write-Err "O modo local exige -Service (ex.: .\scripts\run-local.ps1 -Mode local -Service control-center)"
            exit 1
        }
        Build-Project
        $env:LOCAL_DEVELOPMENT_ENABLED = "true"
        $moduleDir = "$root\ecad-$Service"
        if (-not (Test-Path $moduleDir)) { Write-Err "Modulo 'ecad-$Service' nao encontrado."; exit 1 }
        $jarPath = "$moduleDir\target\ecad-$Service-0.1.0-SNAPSHOT.jar"
        if (-not (Test-Path $jarPath)) {
            $jarPath = (Get-ChildItem "$moduleDir\target\*.jar" | Where-Object { $_.Name -notmatch "original" } | Select-Object -First 1 -ExpandProperty FullName)
        }
        if (-not $jarPath) { Write-Err "JAR nao encontrado em $moduleDir\target."; exit 1 }
        java -jar $jarPath
    }
    "infra" {
        Start-Infrastructure
        Write-Host "=== Infraestrutura Pronta ===" -ForegroundColor Green
        Write-Host "MongoDB: mongodb://localhost:27017" -ForegroundColor Gray
        Write-Host "Azurite: http://localhost:10000" -ForegroundColor Gray
        Write-Host "Kafka: localhost:9094" -ForegroundColor Gray
    }
    "cloud" {
        Start-Infrastructure
        Build-Project
        Run-Services
    }
}
