<#
.SYNOPSIS
    Gerencia e monitora a solucao ECAD PNCP (cloud-agnostic).

.DESCRIPTION
    Script CLI unico para operar e inspecionar a solucao ECAD apos os containers
    estarem em pe (subir containers via .\scripts\run-local.ps1).

    O cadastro de fontes PNCP e automatico no startup do Control Center
    (PncpContractSourcesBootstrap le municipiosPNCP.json), portanto este script
    nao cria nem cadastra fontes manualmente — apenas dispara processamento,
    consulta estado e inspeciona eventos capturados.

    Acoes disponiveis:

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
    │ eventos  │ Lista todos os eventos capturados com detalhes completos,       │
    │          │ exatamente como armazenados no MongoDB/Cosmos. Mostra todos os   │
    │          │ campos de cada evento: codigo, titulo, datas, local, municipio,  │
    │          │ promotor, interpretes, status, evidencias (com links, hashes e   │
    │          │ URLs de armazenamento interno), observacoes da IA, etc.          │
    └──────────┴──────────────────────────────────────────────────────────────────┘

.PARAMETER Action
    Obrigatorio. Valores aceitos:

    run     — Dispara processamento PNCP assincrono
    status  — Verifica estado atual da solucao (health, fontes, jobs, eventos)
    eventos — Lista eventos capturados com detalhes completos do MongoDB/Cosmos

.PARAMETER ControlCenterUrl
    URL base do Control Center (default: http://127.0.0.1:8080).
    Usado por todas as acoes.

.PARAMETER ProcessingEngineUrl
    URL base do Processing Engine (default: http://127.0.0.1:8081).
    Usado por -Action status (health check secao 5).

.PARAMETER Uf
    Filtra por UF (sigla do estado, ex: "BA", "ES").
    Aplicavel a: -Action run (filtra fontes do lote PNCP),
                 -Action status (filtra fontes listadas na secao 2).

.PARAMETER UnidadeEcad
    Filtra por unidade ECAD (ex: "BAHIA", "Rio de Janeiro").
    Aplicavel a: -Action run, -Action status (fontes e eventos),
                 -Action eventos.

.PARAMETER StatusEvento
    Filtra eventos por status (ex: realizado, planejado, cancelado).
    Aplicavel a: -Action status (secao 4), -Action eventos.

.PARAMETER SourceId
    GUID de uma fonte especifica para inspecionar o detalhe completo.
    Aplicavel a: -Action status (substitui a listagem de fontes pela secao 2).

.PARAMETER JobId
    GUID de um job assincrono para verificar status e progresso.
    Aplicavel a: -Action status (secao 3).

.PARAMETER Limite
    Numero maximo de itens a exibir por listagem (default: 50).
    Aplicavel a: -Action run (limite de fontes no lote),
                 -Action status (fontes e eventos),
                 -Action eventos.

.PARAMETER DockerProfile
    Obsoleto. Subir containers deve ser feito via .\scripts\run-local.ps1.

.PARAMETER DryRun
    Mostra o que seria executado (URLs e methods) sem fazer chamadas reais.
    Util para validar comandos antes de executa-los.

.PARAMETER TimeoutSec
    Timeout em segundos para chamadas de API (default: 300).
    Usado principalmente por -Action run (o lote assincrono pode demorar).

.EXAMPLE
    # Disparar processamento PNCP completo
    .\scripts\ecadexecute.ps1 -Action run

.EXAMPLE
    # Disparar processamento filtrando por UF e unidade ECAD
    .\scripts\ecadexecute.ps1 -Action run -Uf BA -UnidadeEcad "BAHIA"

.EXAMPLE
    # Verificar estado geral da solucao
    .\scripts\ecadexecute.ps1 -Action status

.EXAMPLE
    # Acompanhar job assincrono disparado por -Action run
    .\scripts\ecadexecute.ps1 -Action status -JobId "uuid-do-job"

.EXAMPLE
    # Inspecionar detalhes de uma fonte especifica
    .\scripts\ecadexecute.ps1 -Action status -SourceId "uuid-da-fonte"

.EXAMPLE
    # Filtrar eventos por status e unidade ECAD
    .\scripts\ecadexecute.ps1 -Action status -StatusEvento realizado -UnidadeEcad "Rio de Janeiro"

.EXAMPLE
    # Listar eventos com detalhes completos (como salvos no MongoDB/Cosmos)
    .\scripts\ecadexecute.ps1 -Action eventos

.EXAMPLE
    # Listar no maximo 5 eventos detalhados
    .\scripts\ecadexecute.ps1 -Action eventos -Limite 5

.EXAMPLE
    # Listar eventos detalhados filtrados por status
    .\scripts\ecadexecute.ps1 -Action eventos -StatusEvento realizado

.EXAMPLE
    # Simular execucao sem chamadas reais (validar comando)
    .\scripts\ecadexecute.ps1 -Action run -Uf ES -DryRun

.NOTES
    ====================================================================
    Endpoints da API utilizados por este script:
    ====================================================================
      Control Center (porta 8080):
        GET  /api/health                              — health check do servico
        GET  /api/fontes                              — listar fontes cadastradas
        GET  /api/fontes/{id}                         — detalhe de fonte especifica
        GET  /api/fontes/executar-lote-pncp/jobs/{id} — status de job assincrono
        GET  /api/eventos                             — listar eventos capturados
        POST /api/fontes/executar-lote-pncp/async     — disparar lote PNCP assincrono

      Processing Engine (porta 8081):
        GET  /api/health                              — health check do servico
        (retorna status, consumerRunning, services.ai_provider_chain)

    ====================================================================
    Endpoints NAO cobertos por este script (usar API diretamente):
    ====================================================================
      POST /api/fontes/{id}/executar                 — executar fonte individual com IA
      POST /api/fontes/{id}/executarContratoSemIA    — executar fonte sem IA
      POST /api/fontes/executar-lote-pncp            — executar lote PNCP sincrono
      GET  /api/eventos/{id}?municipio=X             — detalhe de evento por ID

    ====================================================================
    Endpoints considerados codigo morto (nao usar):
    ====================================================================
      POST /api/fontes/setup-pncp-urls              — saida nao consumida por nenhum servico
      POST /api/fontes/migrarFontesContratos         — executado automaticamente no startup
      POST /api/fontes                              — criar fonte manual (usar bootstrap)
      PUT  /api/fontes/{id}                           — atualizar fonte (nao aplicavel)
      DELETE /api/fontes/{id}                         — remover fonte (nao aplicavel)
      POST /api/eventos/captured                     — interno (chamado pelo document-scraper)
      GET  /api/metricas/custos                       — metricas de IA (nao monitorado aqui)
      GET  /api/exportacao/*                         — exportacao de planilhas (nao monitorado)
      GET  /api/destinatarios/*                      — gestao de destinatarios (nao monitorado)
      GET  /api/observability/processing-engines     — observabilidade (nao monitorado aqui)

    ====================================================================
    Arquitetura da solucao ECAD:
    ====================================================================
      Control Center (8080)  — Orquestracao: fontes, jobs, eventos, API REST
      Processing Engine (8081) — Consumidor Kafka: processa documentos capturados,
                                 extrai dados com IA, enriquece eventos
      Kafka (9092/9094)      — Topico "captured_documents" (3 particoes)
      MongoDB (27017)        — Armazena eventos, fontes e metadados
      Azurite (10000)        — Blob storage para arquivos de evidencia (PDFs)

    Fluxo: PNCP → document-scraper → Kafka → Processing Engine → MongoDB
           (eventos enriquecidos ficam acessiveis via GET /api/eventos)
#>
param(
    [Parameter(Position = 0, Mandatory = $true)]
    [ValidateSet("run", "status", "eventos")]
    [string]$Action,

    [string]$ControlCenterUrl = "http://127.0.0.1:8080",
    [string]$ProcessingEngineUrl = "http://127.0.0.1:8081",
    [string]$DockerProfile = "full",
    [string]$Uf,
    [string]$UnidadeEcad,
    [Guid]$SourceId,
    [Guid]$JobId,
    [string]$StatusEvento,
    [int]$Limite = 50,
    [Switch]$DryRun,
    [int]$TimeoutSec = 300
)

$ErrorActionPreference = "Stop"
$ccUrl = $ControlCenterUrl.TrimEnd('/')
$peUrl = $ProcessingEngineUrl.TrimEnd('/')

# ============================================================================
# Funcoes auxiliares
# ============================================================================

function Write-Section([string]$Title) {
    Write-Host "`n=== $Title ===" -ForegroundColor Cyan
}

function Write-Ok([string]$Msg) { Write-Host "  [OK] $Msg" -ForegroundColor Green }
function Write-Warn([string]$Msg) { Write-Host "  [!] $Msg" -ForegroundColor Yellow }
function Write-Err([string]$Msg) { Write-Host "  [X] $Msg" -ForegroundColor Red }
function Write-Info([string]$Msg) { Write-Host "  $Msg" -ForegroundColor Gray }

<#
    Invoke-Api — Wrapper para chamadas REST com retry automatico.
    Trata erros transientes (5xx, 429, timeouts) com backoff exponencial.
    Retorna $null em caso de falha persistente.
#>
function Invoke-Api {
    param(
        [string]$Method = "GET",
        [string]$BaseUrl,
        [string]$Path,
        $Body,
        [int]$ApiTimeoutSec = 30
    )
    $uri = "$BaseUrl$Path"
    $params = @{
        Method  = $Method
        Uri     = $uri
    }
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

<#
    Test-ServiceHealth — Verifica a saude de um servico via GET /api/health.
    Retorna o objeto JSON da resposta ou $null se indisponivel.
#>
function Test-ServiceHealth {
    param([string]$Url, [string]$Name)
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
#   Dispara o processamento PNCP assincrono via Control Center.
#   Envia POST /api/fontes/executar-lote-pncp/async com filtros opcionais
#   (UF, unidade ECAD, limite) e retorna o jobId para acompanhamento.
# ============================================================================

function Invoke-Run {
    Write-Section "Disparando processamento PNCP (lote assincrono)"

    # Verificar se o Control Center esta acessivel antes de disparar
    $ccHealth = Test-ServiceHealth -Url $ccUrl -Name "Control Center"
    if (-not $ccHealth -or $ccHealth.status -ne "healthy") {
        Write-Err "Control Center nao esta saudavel. Suba a solucao primeiro:"
        Write-Info ".\scripts\run-local.ps1 -Mode cloud"
        return
    }

    # Construir body com filtros opcionais (uf, unidade_ecad, limite, offset)
    $body = @{}
    if ($Uf) { $body.uf = $Uf }
    if ($UnidadeEcad) { $body.unidade_ecad = $UnidadeEcad }
    if ($Limite -and $Limite -gt 0) { $body.limite = $Limite }

    if ($Uf) { Write-Info "Filtrando por UF: $Uf" }
    if ($UnidadeEcad) { Write-Info "Filtrando por unidade ECAD: $UnidadeEcad" }

    $job = Invoke-Api -Method POST -BaseUrl $ccUrl -Path "/api/fontes/executar-lote-pncp/async" -Body $body -ApiTimeoutSec $TimeoutSec
    if ($DryRun) {
        Write-Warn "[DRY-RUN] Comando nao executado. Remova -DryRun para disparar o processamento."
    } elseif ($job -and $job.jobId) {
        Write-Ok "Job assincrono iniciado: $($job.jobId)"
        Write-Info "Para acompanhar: .\scripts\ecadexecute.ps1 -Action status -JobId $($job.jobId)"
    } elseif ($job) {
        Write-Ok "Comando enviado. Resposta:"
        Write-Host ($job | ConvertTo-Json -Depth 3) -ForegroundColor Green
    } else {
        Write-Err "Falha ao disparar processamento PNCP"
        Write-Info "Verifique os logs: docker compose logs control-center"
    }
}

# ============================================================================
# Action: status
#   Verifica o estado global da solucao em 5 secoes:
#    1. Saude do Control Center e Processing Engine
#    2. Fontes cadastradas (ou detalhe de fonte especifica se -SourceId)
#    3. Status de job assincrono (se -JobId informado)
#    4. Eventos capturados (resumo em uma linha por evento)
#    5. Resumo do Processing Engine (consumer Kafka, provedores IA)
# ============================================================================

function Invoke-Status {
    Write-Section "Verificando estado da solucao ECAD"

    # 1. Health checks
    Write-Section "1. Saude dos servicos"
    $ccHealth = Test-ServiceHealth -Url $ccUrl -Name "Control Center"
    $peHealth = Test-ServiceHealth -Url $peUrl -Name "Processing Engine"

    if (-not $ccHealth) {
        Write-Err "Control Center indisponivel. Suba a solucao primeiro:"
        Write-Info ".\scripts\run-local.ps1 -Mode cloud"
        return
    }

    # 2. Fontes cadastradas (ou detalhe de fonte especifica)
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

    # 3. Job assincrono (se JobId fornecido)
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
#   Lista todos os eventos capturados com detalhes completos, exatamente
#   como armazenados no MongoDB/Cosmos. Mostra todos os campos de cada
#   evento: codigo, titulo, datas, local, promotor, interpretes, status,
#   evidencias (com links, hashes e URLs de armazenamento interno), etc.
# ============================================================================

function Invoke-Eventos {
    Write-Section "Eventos capturados (detalhes completos do MongoDB/Cosmos)"

    # Verifica se o Control Center esta disponivel
    $ccHealth = Test-ServiceHealth -Url $ccUrl -Name "Control Center"
    if (-not $ccHealth) {
        Write-Err "Control Center indisponivel. Suba a solucao primeiro:"
        Write-Info ".\scripts\run-local.ps1 -Mode cloud"
        return
    }

    # Constroi query params
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
        Write-Host "  ID MongoDB:    $($ev.id)" -ForegroundColor DarkGray

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
# Execucao
# ============================================================================

switch ($Action) {
    "run" { Invoke-Run }
    "status" { Invoke-Status }
    "eventos" { Invoke-Eventos }
}

Write-Host "`n=== Concluido ===" -ForegroundColor Cyan
