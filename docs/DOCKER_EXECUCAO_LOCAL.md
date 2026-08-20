# Execução Local e Operação

## Pré-requisitos

- Docker Desktop + Docker Compose
- PowerShell 5.1+
- Java 21+ (para build via `mvndw.ps1`)

## Arquitetura Local

```
Docker Network (ecad-network)
├── MongoDB :27017
├── Azurite :10000-10002 (Blob/Queue/Table)
├── Kafka   :9092/:9094 (KRaft)
└── ECAD Services
    ├── control-center :8080
    └── processing-engine :8081
```

## Perfis do Docker Compose

| Perfil | Serviços | Uso |
|---|---|---|
| `infra` | MongoDB, Azurite, Kafka | Apenas infraestrutura |
| `full` | Infra + serviços ECAD | Stack completa |
| `tools` | deduplicator, log-analyser | Ferramentas CLI |

## Subir a Stack

```powershell
# Modo filesystem (sem Docker) — desenvolvimento rápido
.\scripts\run-local.ps1 -Mode local

# Docker local completo
.\scripts\run-local.ps1 -Mode cloud

# Apenas infraestrutura
.\scripts\run-local.ps1 -Mode infra
```

## Verificação de Saúde

```powershell
# Control Center
curl http://localhost:8080/actuator/health

# Processing Engine
curl http://localhost:8081/actuator/health

# MongoDB
docker exec ecad-mongodb-1 mongosh --eval "db.adminCommand('ping')"

# Kafka
docker exec ecad-kafka-1 /opt/kafka/bin/kafka-topics.sh --list --bootstrap-server localhost:9092
```

## Operação Dia a Dia

### Logs

```powershell
docker compose logs -f control-center
docker compose logs -f processing-engine
docker compose logs --tail=100 document-scraper
```

### Operações PNCP (`ecadexecute.ps1`)

```powershell
# Subir solução completa
.\scripts\ecadexecute.ps1 -Action run

# Status geral (saúde, fontes, eventos)
.\scripts\ecadexecute.ps1 -Action status

# Filtrar por unidade ECAD
.\scripts\ecadexecute.ps1 -Action status -UnidadeEcad "SAO PAULO"

# Status de job assíncrono
.\scripts\ecadexecute.ps1 -Action status -JobId "uuid"

# Importar municípios
.\scripts\ecadexecute.ps1 -Action import -InputCsv .\municipios.csv -ControlCenterUrl http://localhost:8080

# Executar scraping de uma fonte
.\scripts\ecadexecute.ps1 -Action execute -SourceId "uuid" -ControlCenterUrl http://localhost:8080

# Executar lote por UF
.\scripts\ecadexecute.ps1 -Action execute -Uf "SP" -ControlCenterUrl http://localhost:8080
```

| Parâmetro | Descrição |
|---|---|
| `-Action` | `run`, `import`, `execute`, `check`, `status` |
| `-ControlCenterUrl` | URL do Control Center |
| `-InputCsv` | CSV de municípios (para `import`) |
| `-SourceId` | UUID da fonte (para `execute` individual) |
| `-Uf` | Filtra por UF (para `execute` lote) |
| `-SemIA` | Usa rota tradicional (sem IA) |
| `-Keywords` | Palavras-chave para busca |
| `-RateLimitSeconds` | Delay entre requisições (padrão: 1s) |

### Endpoints da API

| Ação | Endpoint | Método |
|---|---|---|
| Health | `/api/health` | GET |
| Listar fontes | `/api/fontes` | GET |
| Detalhe fonte | `/api/fontes/{id}` | GET |
| Executar (com IA) | `/api/fontes/{id}/executar` | POST |
| Executar (sem IA) | `/api/fontes/{id}/executarContratoSemIA` | POST |
| Lote síncrono | `/api/fontes/executar-lote-pncp` | POST |
| Lote assíncrono | `/api/fontes/executar-lote-pncp/async` | POST |
| Status job async | `/api/fontes/executar-lote-pncp/jobs/{jobId}` | GET |
| Listar eventos | `/api/eventos` | GET |

## Backup e Restore MongoDB

### Backup (Dump)

```powershell
docker exec ecad-mongodb-1 mongodump --db ecad-captacao --out /data/backup/$(Get-Date -Format "yyyyMMdd")
docker cp ecad-mongodb-1:/data/backup/20240115 ./backup/
```

### Restore

```powershell
docker cp ./backup/20240115/ecad-captacao ecad-mongodb-1:/data/restore/
docker exec ecad-mongodb-1 mongorestore --db ecad-captacao --drop /data/restore/ecad-captacao
```

> No Azure, o Cosmos DB possui backup automático com restauração point-in-time — não é necessário `mongodump`.

## Rotação de Segredos

| Variável | Rotação |
|---|---|
| `OPENROUTER_API_KEY` | 90 dias |
| `GEMINI_API_KEY` | 90 dias |
| `AZURE_OPENAI_API_KEY` | 90 dias |
| `MONGODB_CONNECTION_STRING` | Conforme política |

Procedimento: atualizar credencial no provedor → `azure_app_settings.json` (local) ou secrets do Container App (Azure) → rolling restart → validar health checks → revogar credencial antiga.

## Escalonamento

### Kafka Consumer Groups

| Grupo | Escalável |
|---|---|
| `cg-processing-engine` | Sim (múltiplas instâncias) |
| `cg-document-scraper` | Sim (múltiplas instâncias) |

> `sga-status-sync` **não** é consumidor Kafka — é batch via scheduler (8h).

## Troubleshooting

### "Falha na execução do AgendamentoScheduler"

Scheduler roda antes das fontes estarem cadastradas. Solução:
```powershell
# Desabilitar temporariamente
$env:CONTROL_CENTER_SCHEDULING_ENABLED="false"
.\scripts\run-local.ps1 -Mode cloud

# Ou seguir sequência correta
docker compose down -v
.\scripts\ecadexecute.ps1 -Action run
```

### "Connection refused" no MongoDB/Kafka

Serviços não estão prontos. Aguarde ou verifique logs:
```powershell
docker compose logs -f mongodb
docker compose logs -f kafka
```

### "Topic not found" no Kafka

```powershell
docker exec ecad-kafka-1 /opt/kafka/bin/kafka-topics.sh --create --if-not-exists --bootstrap-server localhost:9092 --topic scraping_commands
docker exec ecad-kafka-1 /opt/kafka/bin/kafka-topics.sh --create --if-not-exists --bootstrap-server localhost:9092 --topic captured_documents
```

### "Container 'captura-documentos' not found" no Azurite

```powershell
$connStr = "DefaultEndpointsProtocol=http;AccountName=devstoreaccount1;AccountKey=Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==;BlobEndpoint=http://127.0.0.1:10000/devstoreaccount1;"
docker run --rm --network ${projectName}-network mcr.microsoft.com/azure-cli az storage container create --name captura-documentos --connection-string "$connStr"
```

### Kafka Consumer Lag Alto

```powershell
# Verifica lag
docker exec ecad-kafka-1 /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 --group cg-processing-engine --describe

# Reset offsets (cuidado: reprocessa)
docker exec ecad-kafka-1 /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 --group cg-processing-engine --reset-offsets --to-latest --execute --topic captured_documents
```

### MongoDB Conexões Exauridas

```powershell
docker exec ecad-mongodb-1 mongosh --eval "db.serverStatus().connections"
```

### "Município/UF não encontrado em municipiosPNCP.json"

Verifique se o arquivo existe no classpath e se os nomes no CSV batem com o catálogo.

## Limpeza Completa

```powershell
# Para tudo e remove volumes
docker compose down -v

# Remove imagens
docker compose down -v --rmi all

# Remove .localdev (modo filesystem)
Remove-Item -Recurse -Force .localdev
```
