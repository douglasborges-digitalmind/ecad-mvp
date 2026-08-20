# Deploy no Azure — ECAD Java

> Script canônico: `scripts/run-azure.ps1`

## Arquitetura adotada

| Componente | Serviço Azure | Finalidade |
|---|---|---|
| APIs e workers | Azure Container Apps | 4 serviços |
| Imagens | Azure Container Registry (ACR) | Build local, push e armazenamento |
| MongoDB | Cosmos DB for MongoDB (serverless) | Persistência operacional |
| Kafka | Event Hubs Standard (endpoint Kafka) | Tópicos `scraping_commands` e `captured_documents` |
| Objetos | Azure Storage / Blob | Documentos capturados, checkpoints, sessões |
| Logs | Log Analytics Workspace | Logs centralizados |

### Serviços

| Serviço | Ingress | Réplicas (min/max) |
|---|---|---|
| `control-center` | external (HTTPS público) | 1/2 |
| `processing-engine` | internal | 1/2 |
| `document-scraper` | internal | 1/2 |
| `sga-status-sync` | internal | 1/1 |

Somente o **control-center** possui ingresso externo. Os workers são internos.

## Pré-requisitos

- PowerShell 5.1+
- Azure CLI (`az`) instalado e autenticado (`az login`)
- Docker (para build e push de imagens)
- Arquivos `.deploy/azure-resources.json` e `.deploy/azure_app_settings.dev.json` configurados

## Configuração do `.deploy`

### `azure-resources.json`

Define a nomenclatura de todos os recursos Azure. Campos:

| Campo | Descrição | Exemplo |
|---|---|---|
| `resourceGroup` | Resource Group | `DigitalMind-Ecad-Dev` |
| `location` | Região Azure | `brazilsouth` |
| `namePrefix` | Prefixo dos recursos | `ecad-java` |
| `nameSuffix` | Sufixo único (6 chars) | `88266f` |
| `cosmosAccountName` | Cosmos DB account | `ecad-java-cosmos-88266f` |
| `cosmosDatabaseName` | Database name | `ecad-captacao` |
| `storageAccountName` | Storage account | `ecadjava88266fstg` |
| `eventHubsNamespace` | Event Hubs namespace | `ecad-java-evh-88266f` |
| `containerRegistryName` | ACR name | `ecadjava88266facr` |
| `containerAppsEnvironment` | ACA environment | `ecad-java-aca-env-88266f` |
| `logAnalyticsWorkspace` | Log Analytics | `ecad-java-law-88266f` |
| `blobContainers` | Lista de containers Blob | `["captura-documentos", ...]` |
| `eventHubs` | Lista de Event Hubs | `["scraping_commands", "captured_documents"]` |
| `consumerGroups` | Consumer groups por Event Hub | Ver arquivo |
| `cosmosContainers` | Containers Cosmos com partition key | Ver arquivo |

### `azure_app_settings.dev.json` / `azure_app_settings.json`

Lista de env vars enviadas aos Container Apps. Estrutura de cada entrada:

```json
{
  "name": "NOME_DA_VAR",
  "value": "valor",
  "slotSetting": false
}
```

**Importante:** Connection strings (`MONGODB_*`, `KAFKA_*`, `AZURE_STORAGE_*` e legacy
`COSMOS_DB_*`, `EVENT_HUBS_*`, `BLOB_*`) **não** precisam estar neste arquivo — o script
`run-azure.ps1` resolve automaticamente do Azure em runtime via `Resolve-AzureConnectionStrings`.

#### Variáveis do arquivo (40 entradas)

| Grupo | Variáveis |
|---|---|
| **Blob Storage** | `BLOB_STAGING_PREFIX`, `BLOB_PRODUCAO_PREFIX` |
| **IA Providers** | `AI_PROVIDER_CHAIN`, `OPENROUTER_*` (4), `GEMINI_*` (2), `OLLAMA_*` (2), `AZURE_OPENAI_*` (4) |
| **Email** | `EMAIL_CONNECTION_STRING`, `EMAIL_SENDER_ADDRESS` |
| **SGA** | `SGA_OAUTH_URL`, `SGA_BASE_URL`, `SGA_AUTHORIZATION`, `SGA_CLIENT_ID`, `SGA_CLIENT_SECRET`, `SGA_USER`, `SGA_VERIFICATION_ENABLED`, `SGA_TIMEOUT_SECONDS`, `SGA_MAX_RETRIES`, `SGA_RATE_LIMIT_DELAY_MS` |
| **Proxy** | `PROXY_SERVER`, `PROXY_USERNAME`, `PROXY_PASSWORD` |
| **Scraping** | `HEADLESS_BROWSER`, `DELAY_MIN_MS`, `DELAY_MAX_MS`, `MAX_POSTS_POR_PERFIL`, `ROTACAO_CONTA_MIN_PERFIS`, `ROTACAO_CONTA_MAX_PERFIS` |
| **Control Center** | `CONTROL_CENTER_SCHEDULING_ENABLED`, `AGENDAMENTO_INTERVAL_MS` |
| **Kafka Tuning** | `KAFKA_MAX_POLL_INTERVAL_MS`, `KAFKA_MAX_POLL_RECORDS` |
| **Processing Engine** | `PROCESSING_ENGINE_CONSUMER_ENABLED` |

#### Variáveis resolvidas automaticamente (não no JSON)

O script `Resolve-AzureConnectionStrings` consulta o Azure e injeta:

| Variável | Origem |
|---|---|
| `LOCAL_DEVELOPMENT_ENABLED=false` | Hardcoded (modo cloud) |
| `KAFKA_BOOTSTRAP_SERVERS` | Event Hubs FQDN + `:9093` |
| `KAFKA_SECURITY_PROTOCOL=SASL_SSL` | Event Hubs Kafka |
| `KAFKA_SASL_MECHANISM=PLAIN` | Event Hubs Kafka |
| `KAFKA_SASL_JAAS_CONFIG` | Event Hubs key name + key |
| `KAFKA_SCRAPING_COMMANDS_TOPIC` | `azure-resources.json` |
| `KAFKA_CAPTURED_DOCUMENTS_TOPIC` | `azure-resources.json` |
| `MONGODB_CONNECTION_STRING` | Cosmos DB key |
| `MONGODB_DATABASE_NAME` | `azure-resources.json` |
| `AZURE_STORAGE_CONNECTION_STRING` | Storage account key |
| `AZURE_BLOB_CONTAINER_NAME` | `azure-resources.json` |
| `EVENT_HUBS_CONNECTION_STRING` | Event Hubs (legacy fallback) |
| `COSMOS_DB_CONNECTION_STRING` | Cosmos DB (legacy fallback) |
| `COSMOS_DB_DATABASE_NAME` | Cosmos DB (legacy fallback) |
| `BLOB_STORAGE_CONNECTION_STRING` | Storage (legacy fallback) |
| `BLOB_CONTAINER_NAME` | Storage (legacy fallback) |

> **Nota:** As vars legacy (`EVENT_HUBS_*`, `COSMOS_DB_*`, `BLOB_*`) são injetadas porque
> o `control-center` tem fallback para esses nomes via `ControlCenterSettings.pick()`. Os
> demais módulos (`document-scraper`, `processing-engine`, `sga-status-sync`) leem apenas
> os nomes novos (`KAFKA_*`, `MONGODB_*`, `AZURE_STORAGE_*`).

## Uso

### Deploy completo (infra + apps)

```powershell
.\scripts\run-azure.ps1 -Mode all
```

### Apenas infraestrutura

```powershell
.\scripts\run-azure.ps1 -Mode infra
```

Provisiona: Resource Group, ACR, Storage Account + containers, Event Hubs + topics +
consumer groups, Cosmos DB + database + containers, Log Analytics, Container Apps Environment.

### Apenas Container Apps

```powershell
.\scripts\run-azure.ps1 -Mode apps
```

Faz build Docker, push para ACR, resolve connection strings do Azure, e cria/recria
os Container Apps com env vars corretas.

### Serviço específico

```powershell
.\scripts\run-azure.ps1 -Mode apps -Service control-center
```

### Pular build (apenas recriar Container Apps)

```powershell
.\scripts\run-azure.ps1 -Mode apps -SkipBuild
```

### Teardown (remover tudo)

```powershell
.\scripts\run-azure.ps1 -Mode down
```

## Resolução de connection strings

O `Resolve-AzureConnectionStrings` executa em runtime durante o `-Mode apps`:

1. **Cosmos DB** — `az cosmosdb keys list` → connection string primária
2. **Storage** — `az storage account keys list` → connection string com chave primária
3. **Event Hubs** — `az eventhubs namespace authorization-rule keys list` → connection string + SASL config
4. Sobrescreve valores do JSON e injeta vars ausentes
5. Escreve tudo em arquivo `.env` temporário (evita problemas de parsing com `;`, `=`)
6. Container Apps criados com `--env-vars "@$envFile"`

## Bootstrap automático

O `control-center` executa `PncpContractSourcesBootstrap` no startup (ApplicationRunner)
que popula fontes e critérios de extração no MongoDB — **apenas quando
`LOCAL_DEVELOPMENT_ENABLED=false`**. O script injeta essa var automaticamente.

## Monitoramento

### Logs

```powershell
# Logs de um Container App
az containerapp logs show --name control-center-88266f --resource-group DigitalMind-Ecad-Dev

# Follow
az containerapp logs show --name control-center-88266f --resource-group DigitalMind-Ecad-Dev --follow
```

### Execução via API

```powershell
.\scripts\ecadexecute-azure.ps1 -Action status
.\scripts\ecadexecute-azure.ps1 -Action run
```

### Restart

```powershell
.\scripts\ecadexecute-azure.ps1 -Action restart -Service control-center
```
