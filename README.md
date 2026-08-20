# ECAD Java — Captação de Eventos Musicais

Sistema de scraping, extração semântica via IA e sincronização com o SGA/ECAD para identificação de eventos musicais em portais públicos (PNCP).

## Visão Geral

| Módulo | Função | Porta |
|---|---|---|
| **control-center** | API REST, orquestração, agendamento | 8080 |
| **processing-engine** | Pipeline de extração via IA, persistência de eventos | 8081 |
| **document-scraper** | Scraping documental (Playwright + PNCP) | — |
| **sga-status-sync** | Re-verificação em lote do status SGA | — |
| **deduplicator** | CLI — merge de eventos duplicados | — |
| **log-analyser** | CLI — relatório Excel de telemetria | — |

Arquitetura completa em [`docs/ARQUITETURA.md`](docs/ARQUITETURA.md).

## Início Rápido

### Pré-requisitos

- Java 21+, Docker, PowerShell 5.1+
- Azure CLI (`az`) autenticado (para deploy Azure)

### Build

```powershell
.\mvndw.ps1 clean package -DskipTests   # sem testes
.\mvndw.ps1 clean package               # com testes
```

### Modos de Execução

| Modo | Infra | Comando |
|---|---|---|
| **Local** (filesystem) | `.localdev/` (JSON, filas locais) | `.\scripts\run-local.ps1 -Mode local` |
| **Docker local** | MongoDB, Azurite, Kafka | `.\scripts\run-local.ps1 -Mode cloud` |
| **Azure** | Cosmos DB, Event Hubs, Blob | `.\scripts\run-azure.ps1 -Mode all` |

Para o modo local, configure `.env`:
```powershell
Copy-Item .env.example .env
# Editar .env: LOCAL_DEVELOPMENT_ENABLED=true (local) ou false (Docker/Azure)
```

## Variáveis de Ambiente

Grupos principais do `.env`:

| Grupo | Variáveis |
|---|---|
| **Modo** | `LOCAL_DEVELOPMENT_ENABLED`, `COMPOSE_PROJECT_NAME` |
| **IA** | `AI_PROVIDER_CHAIN`, `OPENROUTER_API_KEY`, `GEMINI_API_KEY`, `AZURE_OPENAI_*` |
| **MongoDB** | `MONGODB_CONNECTION_STRING`, `MONGODB_DATABASE_NAME` |
| **Storage** | `AZURE_STORAGE_CONNECTION_STRING`, `AZURE_BLOB_CONTAINER_NAME` |
| **Kafka** | `KAFKA_BOOTSTRAP_SERVERS`, `KAFKA_*_TOPIC`, `KAFKA_CLUSTER_ID` |
| **SGA** | `SGA_VERIFICATION_ENABLED`, `SGA_OAUTH_URL`, `SGA_BASE_URL`, `SGA_CLIENT_*` |
| **Control Center** | `CONTROL_CENTER_SCHEDULING_ENABLED`, `AGENDAMENTO_INTERVAL_MS` |

> No Azure, connection strings são resolvidas automaticamente pelo `run-azure.ps1`. Veja [`docs/AZURE_DEPLOYMENT_Java.md`](docs/AZURE_DEPLOYMENT_Java.md).

## Scripts

| Script | Descrição |
|---|---|
| `scripts/run-local.ps1` | Stack local (modos: `local`, `infra`, `cloud`) |
| `scripts/run-azure.ps1` | Deploy Azure (modos: `infra`, `apps`, `all`, `down`) |
| `scripts/ecadexecute.ps1` | Operações PNCP local: `run`, `status`, `eventos` |
| `scripts/ecadexecute-azure.ps1` | Operações PNCP Azure: `run`, `status`, `eventos`, `logs`, `restart` |
| `scripts/convert-pncp-csv.ps1` | ~~Converte CSV de municípios PNCP~~ (obsoleto — fontes criadas via `municipiosPNCP.json` no startup) |

### Operações PNCP (`ecadexecute.ps1`)

```powershell
# Disparar processamento PNCP (lote assíncrono)
.\scripts\ecadexecute.ps1 -Action run

# Disparar processamento filtrando por UF
.\scripts\ecadexecute.ps1 -Action run -Uf "SP"

# Status geral (saúde, fontes, eventos, processing engine)
.\scripts\ecadexecute.ps1 -Action status

# Status de um job assíncrono específico
.\scripts\ecadexecute.ps1 -Action status -JobId "uuid-do-job"

# Listar eventos capturados (detalhes completos)
.\scripts\ecadexecute.ps1 -Action eventos

# Listar eventos filtrando por unidade ECAD e status
.\scripts\ecadexecute.ps1 -Action eventos -UnidadeEcad "SP-001" -StatusEvento "realizado"
```

| Parâmetro | Obrigatório | Descrição |
|---|---|---|
| `-Action` | Sim | `run`, `status`, `eventos` |
| `-ControlCenterUrl` | — | URL do Control Center (padrão: `http://127.0.0.1:8080`) |
| `-ProcessingEngineUrl` | — | URL do Processing Engine (padrão: `http://127.0.0.1:8081`) |
| `-Uf` | — | Filtra por UF (em `run` e `status`) |
| `-UnidadeEcad` | — | Filtra por unidade ECAD (em `status` e `eventos`) |
| `-SourceId` | — | Detalha uma fonte específica (em `status`) |
| `-JobId` | — | Consulta status de job assíncrono (em `status`) |
| `-StatusEvento` | — | Filtra eventos por status (em `status` e `eventos`) |
| `-Limite` | — | Máximo de itens exibidos (padrão: 50) |
| `-DryRun` | — | Não executa chamadas REST, apenas exibe |
| `-TimeoutSec` | — | Timeout das chamadas REST (padrão: 300) |

### Endpoints da API REST

| Endpoint | Método | Descrição |
|---|---|---|
| `/api/health` | GET | Health check agregado |
| `/api/fontes` | GET | Lista fontes |
| `/api/fontes/{id}` | GET | Detalhe de fonte |
| `/api/fontes/{id}/executar` | POST | Executa scraping (com IA) |
| `/api/fontes/{id}/executarContratoSemIA` | POST | Executa scraping (sem IA) |
| `/api/fontes/executar-lote-pncp` | POST | Lote síncrono |
| `/api/fontes/executar-lote-pncp/async` | POST | Lote assíncrono |
| `/api/fontes/executar-lote-pncp/jobs/{jobId}` | GET | Status de job assíncrono |
| `/api/eventos` | GET | Lista eventos |
| `/actuator/health` | GET | Health check Spring |

## Configuração Azure (`.deploy/`)

| Arquivo | Descrição |
|---|---|
| `azure-resources.json` | Nomenclatura de recursos (RG, Cosmos, Storage, Event Hubs, ACR) |
| `azure_app_settings.json` | Env vars de produção |
| `azure_app_settings.dev.json` | Env vars de desenvolvimento |

**Secrets que devem ser preenchidos no JSON:**

| Variável | Descrição |
|---|---|
| `OPENROUTER_API_KEY` | API key OpenRouter |
| `GEMINI_API_KEY` | API key Google Gemini |
| `AZURE_OPENAI_ENDPOINT` | Endpoint Azure OpenAI |
| `AZURE_OPENAI_API_KEY` | API key Azure OpenAI |
| `AZURE_OPENAI_DEPLOYMENT` | Nome do deployment |
| `SGA_CLIENT_ID` / `SGA_CLIENT_SECRET` | Credenciais OAuth2 SGA |

> **Importante:** O `run-azure.ps1` trata essas chaves como *secrets* do Container App (não env vars comuns) para evitar mascaramento pelo Azure.

**Connection strings resolvidas automaticamente** (não incluir no JSON): `MONGODB_*`, `KAFKA_*`, `AZURE_STORAGE_*`.

Tutorial completo em [`docs/AZURE_DEPLOYMENT_Java.md`](docs/AZURE_DEPLOYMENT_Java.md).

## Documentação

| Documento | Conteúdo |
|---|---|
| [Arquitetura](docs/ARQUITETURA.md) | Arquitetura completa, módulos, fluxo, modelo de dados |
| [Deploy Azure](docs/AZURE_DEPLOYMENT_Java.md) | Deploy no Azure Container Apps |
| [Execução Local](docs/DOCKER_EXECUCAO_LOCAL.md) | Docker local, troubleshooting, operação |
| [Índice](docs/DOCUMENTACAO_GERAL.md) | Índice da documentação |
# ecadmvp
