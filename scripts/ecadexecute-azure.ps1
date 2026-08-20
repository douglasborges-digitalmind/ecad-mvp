<#
.SYNOPSIS
    Gerencia e monitora a solucao ECAD PNCP no Azure Container Apps.

.DESCRIPTION
    Versao Azure do ecadexecute.ps1. Em vez de apontar para localhost:8080/8081,
    descobre automaticamente os FQDNs dos Container Apps a partir de
    .deploy/azure-resources.json e az containerapp show.

    Acoes disponiveis (mesmas do ecadexecute.ps1):

    ┌──────────┬──────────────────────────────────────────────────────────────────┐
    │ Action   │ Descricao                                                        │
    ├──────────┼──────────────────────────────────────────────────────────────────┤
    │ run      │ Dispara o processamento PNCP assincrono via Control Center.      │
    │          │ Envia POST /api/fontes/executar-lote-pncp/async com filtros       │
    │          │ opcionais (UF, unidade ECAD, limite) e retorna o jobId para      │
    │          │ acompanhamento posterior via -Action status -JobId.              │
    ├──────────┼──────────────────────────────────────────────────────────────────┤
    │ status   │ Verifica o estado global da solucao em 5 secoes:                 │
    │          │  1. Saude do Control Center e Processing Engine (GET /api/health)│
    │          │  2. Fontes cadastradas (GET /api/fontes) ou detalhe de fonte      │
    │          │     especifica (GET /api/fontes/{id}) se -SourceId informado      │
    │          │  3. Status de job assincrono se -JobId informado                 │
    │          │  4. Eventos capturados — resumo em uma linha por evento           │
    │          │     (codigo | municipio/UF | status | status_sga)               │
    │          │  5. Resumo do Processing Engine (consumer Kafka, provedores IA)   │
    ├──────────┼──────────────────────────────────────────────────────────────────┤
    │ eventos  │ Lista eventos capturados com detalhes completos,                 │
    │          │ exatamente como armazenados no Cosmos DB.                        │
    ├──────────┼──────────────────────────────────────────────────────────────────┤
    │ logs     │ Mostra os logs recentes de um Container App especifico (-Service)│
    ├──────────┼──────────────────────────────────────────────────────────────────┤
    │ restart  │ Reinicia um Container App especifico (-Service) ou todos.        │
    └──────────┴──────────────────────────────────────────────────────────────────┘

.PARAMETER Action
    Obrigatorio. Valores aceitos: run, status, eventos, logs, restart.

.PARAMETER Service
    Nome do servico para acoes logs/restart (ex: control-center, processing-engine).
    Default para restart: todos os servicos.

.PARAMETER Uf
    Filtra por UF (sigla do estado, ex: "BA", "ES").

.PARAMETER UnidadeEcad
    Filtra por unidade ECAD (ex: "BAHIA", "Rio de Janeiro").

.PARAMETER SourceId
    GUID de uma fonte especifica para inspecionar o detalhe completo.

.PARAMETER JobId
    GUID de um job assincrono para verificar status e progresso.

.PARAMETER StatusEvento
    Filtra eventos por status (ex: realizado, planejado, cancelado).

.PARAMETER Limite
    Numero maximo de itens a exibir por listagem (default: 50).

.PARAMETER DryRun
    Mostra o que seria executado (URLs e methods) sem fazer chamadas reais.

.PARAMETER TimeoutSec
    Timeout em segundos para chamadas de API (default: 300).

.PARAMETER ControlCenterUrl
    URL base do Control Center. Se omitido, descobre automaticamente via FQDN
    do Container App. Use para sobrescrever a descoberta automatica.

.PARAMETER ProcessingEngineUrl
    URL base do Processing Engine. Se omitido, descobre automaticamente.
    Nota: o Processing Engine tem ingress internal no Azure; para acessa-lo
    de fora do ambiente ACA, use um tunnel ou ajuste o ingress para external.

.EXAMPLE
    # Disparar processamento PNCP completo no Azure
    .\scripts\ecadexecute-azure.ps1 -Action run

.EXAMPLE
    # Verificar estado geral da solucao no Azure
    .\scripts\ecadexecute-azure.ps1 -Action status

.EXAMPLE
    # Acompanhar job assincrono
    .\scripts\ecadexecute-azure.ps1 -Action status -JobId "uuid-do-job"

.EXAMPLE
    # Ver logs do control-center
    .\scripts\ecadexecute-azure.ps1 -Action logs -Service control-center

.EXAMPLE
    # Reiniciar todos os servicos
    .\scripts\ecadexecute-azure.ps1 -Action restart

.EXAMPLE
    # Listar eventos detalhados
    .\scripts\ecadexecute-azure.ps1 -Action eventos -Limite 10

.NOTES
    Requer: Azure CLI (az) autenticado, extensao containerapp.
    Descoberta de FQDN: az containerapp show --query properties.configuration.ingress.fqdn
#>
param(
    [Parameter(Position = 0, Mandatory = $true)]
    [ValidateSet("run", "status", "eventos", "logs", "restart")]
    [string]$Action,

    [string]$Service,
    [string]$Uf,
    [string]$UnidadeEcad,
    [Guid]$SourceId,
    [Guid]$JobId,
    [string]$StatusEvento,
    [int]$Limite = 50,
    [Switch]$DryRun,
    [int]$TimeoutSec = 300,
    [string]$ControlCenterUrl,
    [string]$ProcessingEngineUrl
)

$ErrorActionPreference = "Stop"
$root = (Resolve-Path "$PSScriptRoot\..").Path
$resourcesFile = Join-Path $root ".deploy\azure-resources.json"

# Carrega configuracao de recursos Azure
if (-not (Test-Path $resourcesFile)) {
    Write-Host "[X] Arquivo nao encontrado: $resourcesFile" -ForegroundColor Red
    exit 1
}
$cfg = Get-Content $resourcesFile -Raw | ConvertFrom-Json
$rg     = $cfg.resourceGroup
$suffix = $cfg.nameSuffix

# Servicos ECAD que viram Container Apps (mesma lista do run-azure.ps1)
$ecadServices = @(
    @{ Name = "control-center";    Ingress = $true },
    @{ Name = "processing-engine"; Ingress = $false },
    @{ Name = "document-scraper";  Ingress = $false },
    @{ Name = "sga-status-sync";   Ingress = $false }
)

# ============================================================================
# Descoberta de FQDNs dos Container Apps
# ============================================================================
function Get-AppFqdn {
    param([string]$SvcName)
    $appName = "$SvcName-$suffix"
    $fqdn = az containerapp show --name $appName --resource-group $rg `
        --query "properties.configuration.ingress.fqdn" -o tsv 2>$null
    if (-not $fqdn) {
        Write-Host "[X] Container App '$appName' nao encontrado ou sem FQDN." -ForegroundColor Red
        Write-Host "    Verifique se o deploy foi feito: .\scripts\run-azure.ps1 -Mode apps" -ForegroundColor Gray
        return $null
    }
    return "https://$fqdn"
}

function Get-AppUrl {
    param([string]$SvcName)
    $appName = "$SvcName-$suffix"
    $fqdn = az containerapp show --name $appName --resource-group $rg `
        --query "properties.configuration.ingress.fqdn" -o tsv 2>$null
    if (-not $fqdn) {
        return $null
    }
    return "https://$fqdn"
}

# Descobre URLs automaticamente se nao fornecidas
if (-not $ControlCenterUrl) {
    $ControlCenterUrl = Get-AppUrl -SvcName "control-center"
    if (-not $ControlCenterUrl) {
        Write-Host "[X] Nao foi possivel descobrir a URL do Control Center." -ForegroundColor Red
        exit 1
    }
}
if (-not $ProcessingEngineUrl) {
    $ProcessingEngineUrl = Get-AppUrl -SvcName "processing-engine"
}

$ccUrl = $ControlCenterUrl.TrimEnd('/')
$peUrl = $ProcessingEngineUrl.TrimEnd('/')

# ============================================================================
# Funcoes auxiliares (mesmas do ecadexecute.ps1)
# ============================================================================

function Write-Section([string]$Title) {
    Write-Host "`n=== $Title ===" -ForegroundColor Cyan
}

function Write-Ok([string]$Msg) { Write-Host "  [OK] $Msg" -ForegroundColor Green }
function Write-Warn([string]$Msg) { Write-Host "  [!] $Msg" -ForegroundColor Yellow }
function Write-Err([string]$Msg) { Write-Host "  [X] $Msg" -ForegroundColor Red }
function Write-Info([string]$Msg) { Write-Host "  $Msg" -ForegroundColor Gray }

function Invoke-Api {
    param(
        [string]$Method = "GET",
        [string]$BaseUrl,
        [string]$Path,
        $Body,
        [int]$ApiTimeoutSec = 30
    )
    $uri = "$BaseUrl$Path"
    $params = @{ Method = $Method; Uri = $uri }
    if ($Body -and $Body.Keys.Count -gt 0) {
        $params.Body = ($Body | ConvertTo-Json -Depth 5 -Compress)
    } elseif ($Method -eq "POST" -or $Method -eq "PUT") {
        $params.Body = "{}"
    }
    if ($Method -eq "POST" -or $Method -eq "PUT") {
        $params.ContentType = "application/json"
    }
    Write-Info "$Method $uri"
    if ($DryRun) {
        Write-Warn "[DRY-RUN] Nao executando chamada."
        return $null
    }
    $params.TimeoutSec = $ApiTimeoutSec

    $maxRetries = 3
    $backoffSec = 2
    for ($attempt = 1; $attempt -le $maxRetries; $attempt++) {
        try {
            return Invoke-RestMethod @params
        } catch {
            $isTransient = $false
            $status = $null
            if ($_.Exception.Response) {
                $status = [int]$_.Exception.Response.StatusCode
                if ($status -ge 500 -or $status -eq 429) { $isTransient = $true }
            }
            if ($_.Exception -is [System.Net.WebException] -or $_.Exception.Message -match "timeout|connection|Unable to connect") {
                $isTransient = $true
            }
            if (-not $isTransient -or $attempt -eq $maxRetries) {
                Write-Err "Falha: $($_.Exception.Message)"
                if ($_.ErrorDetails -and $_.ErrorDetails.Message) {
                    Write-Host "    Response: $($_.ErrorDetails.Message)" -ForegroundColor Red
                }
                return $null
            }
            Write-Warn "[RETRY $attempt/$maxRetries] Erro transiente (status=$status). Aguardando ${backoffSec}s..."
            Start-Sleep -Seconds $backoffSec
            $backoffSec *= 2
        }
    }
}

function Test-ServiceHealth {
    param([string]$Url, [string]$Name)
    if (-not $Url) {
        Write-Err "$Name sem FQDN disponivel (ingress internal?)."
        return $null
    }
    try {
        $response = Invoke-RestMethod -Uri "$Url/api/health" -Method GET -TimeoutSec 30
        $status = $response.status
        $component = $response.component
        if ($status -eq "healthy") {
            Write-Ok "$Name saudavel (status=$status, component=$component)"
        } else {
            Write-Warn "$Name degradado (status=$status)"
        }
        return $response
    } catch {
        Write-Err "$Name indisponivel: $($_.Exception.Message)"
        return $null
    }
}

# ============================================================================
# Action: run
# ============================================================================
function Invoke-Run {
    Write-Section "Disparando processamento PNCP (lote assincrono) no Azure"

    $ccHealth = Test-ServiceHealth -Url $ccUrl -Name "Control Center"
    if (-not $ccHealth -or $ccHealth.status -ne "healthy") {
        Write-Err "Control Center nao esta saudavel."
        Write-Info "Verifique: .\scripts\ecadexecute-azure.ps1 -Action logs -Service control-center"
        return
    }

    $body = @{}
    if ($Uf) { $body.uf = $Uf }
    if ($UnidadeEcad) { $body.unidade_ecad = $UnidadeEcad }
    if ($Limite -and $Limite -gt 0) { $body.limite = $Limite }

    if ($Uf) { Write-Info "Filtrando por UF: $Uf" }
    if ($UnidadeEcad) { Write-Info "Filtrando por unidade ECAD: $UnidadeEcad" }

    $job = Invoke-Api -Method POST -BaseUrl $ccUrl -Path "/api/fontes/executar-lote-pncp/async" -Body $body -ApiTimeoutSec $TimeoutSec
    if ($DryRun) {
        Write-Warn "[DRY-RUN] Comando nao executado."
    } elseif ($job -and $job.jobId) {
        Write-Ok "Job assincrono iniciado: $($job.jobId)"
        Write-Info "Para acompanhar: .\scripts\ecadexecute-azure.ps1 -Action status -JobId $($job.jobId)"
    } elseif ($job) {
        Write-Ok "Comando enviado. Resposta:"
        Write-Host ($job | ConvertTo-Json -Depth 3) -ForegroundColor Green
    } else {
        Write-Err "Falha ao disparar processamento PNCP"
        Write-Info "Verifique os logs: .\scripts\ecadexecute-azure.ps1 -Action logs -Service control-center"
    }
}

# ============================================================================
# Action: status
# ============================================================================
function Invoke-Status {
    Write-Section "Verificando estado da solucao ECAD no Azure"
    Write-Info "Control Center:    $ccUrl"
    Write-Info "Processing Engine: $peUrl"

    # 1. Health checks
    Write-Section "1. Saude dos servicos"
    $ccHealth = Test-ServiceHealth -Url $ccUrl -Name "Control Center"
    $peHealth = Test-ServiceHealth -Url $peUrl -Name "Processing Engine"

    if (-not $ccHealth) {
        Write-Err "Control Center indisponivel."
        Write-Info "Verifique: .\scripts\ecadexecute-azure.ps1 -Action logs -Service control-center"
        return
    }

    # 2. Fontes cadastradas
    if ($SourceId) {
        Write-Section "2. Detalhes da fonte: $SourceId"
        $fonte = Invoke-Api -BaseUrl $ccUrl -Path "/api/fontes/$SourceId"
        if ($fonte) {
            Write-Host ($fonte | ConvertTo-Json -Depth 4) -ForegroundColor Green
        }
    } else {
        Write-Section "2. Fontes cadastradas"
        $queryParams = @()
        if ($UnidadeEcad) { $queryParams += "unidade_ecad=$UnidadeEcad" }
        if ($Uf) { $queryParams += "uf=$Uf" }
        $path = "/api/fontes"
        if ($queryParams.Count -gt 0) { $path += "?" + ($queryParams -join "&") }
        $fontes = Invoke-Api -BaseUrl $ccUrl -Path $path
        if ($fontes) {
            $fontesList = if ($fontes -is [array]) { $fontes } else { @($fontes) }
            Write-Ok "$($fontesList.Count) fonte(s) cadastrada(s)"
            $fontesList | Select-Object -First $Limite | ForEach-Object {
                $canaisAtivos = @($_.canais_scraping | Where-Object { $_.ativo }).Count
                $cor = if ($canaisAtivos -gt 0) { "Green" } else { "Red" }
                Write-Host "  $($_.id) | $($_.nome) | $($_.unidade_ecad) | Canais ativos: $canaisAtivos" -ForegroundColor $cor
            }
            if ($fontesList.Count -gt $Limite) {
                Write-Info "... e mais $($fontesList.Count - $Limite) fonte(s). Use -Limite para ver mais."
            }
        } else {
            Write-Warn "Nao foi possivel obter a lista de fontes."
        }
    }

    # 3. Job assincrono
    if ($JobId) {
        Write-Section "3. Status do job assincrono: $JobId"
        $job = Invoke-Api -BaseUrl $ccUrl -Path "/api/fontes/executar-lote-pncp/jobs/$JobId"
        if ($job) {
            Write-Ok "Status: $($job.status)"
            Write-Info "Progresso: $($job.fontes_processadas)/$($job.fontes_planejadas)"
            Write-Info "Criado em: $($job.criado_em)"
            if ($job.erro) { Write-Err "Erro: $($job.erro)" }
            if ($job.resultado_detalhado) {
                Write-Host ($job.resultado_detalhado | ConvertTo-Json -Depth 3) -ForegroundColor Gray
            }
        } else {
            Write-Warn "Job nao encontrado ou ainda nao iniciado."
        }
    }

    # 4. Eventos capturados
    Write-Section "4. Eventos capturados"
    $eventoParams = @()
    if ($UnidadeEcad) { $eventoParams += "unidade_ecad=$UnidadeEcad" }
    if ($StatusEvento) { $eventoParams += "status=$StatusEvento" }
    $eventoPath = "/api/eventos"
    if ($eventoParams.Count -gt 0) { $eventoPath += "?" + ($eventoParams -join "&") }
    $eventos = Invoke-Api -BaseUrl $ccUrl -Path $eventoPath
    if ($eventos) {
        $eventosList = if ($eventos -is [array]) { $eventos } else { @($eventos) }
        Write-Ok "$($eventosList.Count) evento(s) encontrado(s)"
        if ($StatusEvento) { Write-Info "Filtrado por status: $StatusEvento" }
        $eventosList | Select-Object -First $Limite | ForEach-Object {
            Write-Host "  $($_.codigo_evento) | $($_.municipio)/$($_.uf) | Status: $($_.status) | SGA: $($_.status_sga)" -ForegroundColor Gray
        }
        if ($eventosList.Count -gt $Limite) {
            Write-Info "... e mais $($eventosList.Count - $Limite) evento(s). Use -Limite para ver mais."
        }
    } else {
        Write-Warn "Nenhum evento encontrado (ou erro ao consultar)."
    }

    # 5. Resumo do Processing Engine
    if ($peHealth) {
        Write-Section "5. Processing Engine"
        $consumerRunning = $peHealth.consumerRunning
        if ($consumerRunning) { Write-Ok "Consumer Kafka ativo" }
        else { Write-Warn "Consumer Kafka parado" }

        $aiProviders = $peHealth.services.'ai_provider_chain'
        if ($aiProviders) {
            $configured = $aiProviders.configuredProviders
            if ($configured -and $configured.Count -gt 0) {
                Write-Ok "Provedores IA configurados: $($configured -join ', ')"
            } else {
                Write-Warn "Nenhum provedor IA configurado"
            }
        }
    }
}

# ============================================================================
# Action: eventos
# ============================================================================
function Invoke-Eventos {
    Write-Section "Eventos capturados (detalhes completos do Cosmos DB)"

    $ccHealth = Test-ServiceHealth -Url $ccUrl -Name "Control Center"
    if (-not $ccHealth) {
        Write-Err "Control Center indisponivel."
        Write-Info "Verifique: .\scripts\ecadexecute-azure.ps1 -Action logs -Service control-center"
        return
    }

    $queryParams = @()
    if ($UnidadeEcad) { $queryParams += "unidade_ecad=$UnidadeEcad" }
    if ($StatusEvento) { $queryParams += "status=$StatusEvento" }
    $path = "/api/eventos"
    if ($queryParams.Count -gt 0) { $path += "?" + ($queryParams -join "&") }

    $eventos = Invoke-Api -BaseUrl $ccUrl -Path $path
    if (-not $eventos) {
        Write-Warn "Nenhum evento encontrado (ou erro ao consultar)."
        return
    }

    $eventosList = if ($eventos -is [array]) { $eventos } else { @($eventos) }
    Write-Ok "$($eventosList.Count) evento(s) encontrado(s)"
    if ($StatusEvento) { Write-Info "Filtrado por status: $StatusEvento" }
    if ($UnidadeEcad) { Write-Info "Filtrado por unidade ECAD: $UnidadeEcad" }
    Write-Host ""

    $eventosList | Select-Object -First $Limite | ForEach-Object {
        $ev = $_
        Write-Host "================================================================" -ForegroundColor Cyan
        Write-Host "  Codigo:        $($ev.codigo_evento)" -ForegroundColor White
        Write-Host "  Titulo:        $($ev.titulo)" -ForegroundColor White
        Write-Host "  Data inicio:   $($ev.data_inicio)" -ForegroundColor Gray
        Write-Host "  Data termino:  $($ev.data_termino)" -ForegroundColor Gray
        Write-Host "  Hora:          $($ev.hora)" -ForegroundColor Gray
        Write-Host "  Local:         $($ev.local)" -ForegroundColor Gray
        Write-Host "  Municipio/UF:  $($ev.municipio) / $($ev.uf)" -ForegroundColor Gray
        Write-Host "  Unidade ECAD:  $($ev.unidade_ecad)" -ForegroundColor Gray
        Write-Host "  Promotor:      $($ev.promotor_nome) (CNPJ: $($ev.promotor_cnpj))" -ForegroundColor Gray
        if ($ev.interpretes -and $ev.interpretes.Count -gt 0) {
            Write-Host "  Interpretes:   $($ev.interpretes -join ', ')" -ForegroundColor Gray
        }
        Write-Host "  Tipo musica:   $($ev.tipo_musica)" -ForegroundColor Gray
        Write-Host "  Status:        $($ev.status)" -ForegroundColor $(if ($ev.status -eq 'realizado') { 'Green' } else { 'Yellow' })
        Write-Host "  Status SGA:    $($ev.status_sga)" -ForegroundColor $(if ($ev.status_sga -eq 'verificado') { 'Green' } else { 'Yellow' })
        Write-Host "  Completude:    $($ev.nivel_completude)" -ForegroundColor Gray
        Write-Host "  Fonte primaria: $($ev.fonte_primaria)" -ForegroundColor Gray
        Write-Host "  Descoberto em: $($ev.data_descoberta)" -ForegroundColor Gray
        Write-Host "  Atualizado em: $($ev.data_atualizacao)" -ForegroundColor Gray
        Write-Host "  ID Fonte:      $($ev.id_fonte_captacao)" -ForegroundColor DarkGray
        Write-Host "  ID Cosmos:     $($ev.id)" -ForegroundColor DarkGray

        if ($ev.observacoes_ia) {
            Write-Host "  Obs IA:        $($ev.observacoes_ia)" -ForegroundColor DarkGray
        }

        if ($ev.evidencias -and $ev.evidencias.Count -gt 0) {
            Write-Host "  Evidencias ($($ev.evidencias.Count)):" -ForegroundColor Gray
            $ev.evidencias | ForEach-Object {
                Write-Host "    [$($_.sequencia)] $($_.tipo)" -ForegroundColor DarkGray
                Write-Host "        Link fonte:     $($_.link_fonte)" -ForegroundColor DarkGray
                Write-Host "        Armaz. interno: $($_.url_armazenamento_interno)" -ForegroundColor DarkGray
                Write-Host "        Data captura:   $($_.data_captura)" -ForegroundColor DarkGray
                Write-Host "        Hash arquivo:   $($_.hash_arquivo)" -ForegroundColor DarkGray
            }
        }

        Write-Host ""
    }

    if ($eventosList.Count -gt $Limite) {
        Write-Info "... e mais $($eventosList.Count - $Limite) evento(s). Use -Limite para ver mais."
    }
}

# ============================================================================
# Action: logs (especifico do Azure)
# ============================================================================
function Invoke-Logs {
    Write-Section "Logs do Container App"

    if (-not $Service) {
        Write-Err "Use -Service para especificar qual Container App."
        Write-Info "Disponiveis: $($ecadServices.Name -join ', ')"
        return
    }

    $appName = "$Service-$suffix"
    $exists = az containerapp show --name $appName --resource-group $rg --query "name" -o tsv 2>$null
    if (-not $exists) {
        Write-Err "Container App '$appName' nao encontrado."
        return
    }

    Write-Info "Container App: $appName"
    Write-Info "Ultimos $Limite linhas de log:"
    Write-Host ""

    az containerapp logs show --name $appName --resource-group $rg --tail $Limite 2>&1
}

# ============================================================================
# Action: restart (especifico do Azure)
# ============================================================================
function Invoke-Restart {
    Write-Section "Reiniciando Container Apps"

    $targets = if ($Service) {
        $ecadServices | Where-Object { $_.Name -eq $Service }
    } else {
        $ecadServices
    }

    if ($targets.Count -eq 0) {
        Write-Err "Servico '$Service' nao encontrado."
        Write-Info "Disponiveis: $($ecadServices.Name -join ', ')"
        return
    }

    foreach ($svc in $targets) {
        $appName = "$($svc.Name)-$suffix"
        $exists = az containerapp show --name $appName --resource-group $rg --query "name" -o tsv 2>$null
        if (-not $exists) {
            Write-Warn "Nao encontrado: $appName"
            continue
        }
        Write-Info "Reiniciando $appName ..."
        az containerapp revision restart --name $appName --resource-group $rg 2>&1 | Out-Null
        if ($LASTEXITCODE -eq 0) {
            Write-Ok "Reiniciado: $appName"
        } else {
            Write-Err "Falha ao reiniciar: $appName"
        }
    }
}

# ============================================================================
# Execucao
# ============================================================================

Write-Host "ECAD Azure — Resource Group: $rg" -ForegroundColor DarkGray

switch ($Action) {
    "run"     { Invoke-Run }
    "status"  { Invoke-Status }
    "eventos" { Invoke-Eventos }
    "logs"    { Invoke-Logs }
    "restart" { Invoke-Restart }
}

Write-Host "`n=== Concluido ===" -ForegroundColor Cyan
