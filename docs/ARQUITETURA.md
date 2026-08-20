# ECAD Java — Arquitetura e Componentes

> Documento técnico canônico do sistema ECAD Java.

## 1. Visão Geral

Solução multi-módulo em Java 21 / Spring Boot 3.5.7 para captura, extração, enriquecimento, verificação e deduplicação de eventos musicais a partir de fontes públicas (PNCP, diários oficiais).

A arquitetura é **cloud-agnostic**: o mesmo código roda em modo **Local** (filesystem) ou **Cloud** (MongoDB, Kafka, Azure Blob), alternando via `LOCAL_DEVELOPMENT_ENABLED` e factories que inspecionam a disponibilidade do `MongoClient`.

### Pilha Tecnológica

| Componente | Tecnologia |
|---|---|
| Linguagem/RT | Java 21 |
| Framework | Spring Boot 3.5.7 |
| Persistência | MongoDB (driver síncrono) |
| Object Storage | Azure Blob / Azurite |
| Mensageria | Apache Kafka / Event Hubs |
| Browser | Playwright 1.49 |
| PDF/Excel | PDFBox 3.0.3 / Apache POI 5.3 |
| Build | Maven (wrapper `mvndw`) |

## 2. Módulos

| Módulo | Porta | Função |
|---|---|---|
| `ecad-shared` | — | Kernel: domínio, contratos, infra cloud-agnostic, repositórios |
| `ecad-control-center` | 8080 | API REST, agendador, orquestração de scraping |
| `ecad-processing-engine` | 8081 | Worker Kafka, pipeline IA multi-provider, extração de eventos |
| `ecad-document-scraper` | — | Worker Kafka, Playwright, scraping PNCP/diários |
| `ecad-sga-status-sync` | — | Sincronização status evento ↔ SGA (batch) |
| `ecad-deduplicator` | CLI | Deduplicação (hash + fuzzy + IA opcional) |
| `ecad-log-analyser` | CLI | Relatório Excel de telemetria |
| `ecad-integration-tests` | — | Testes de equivalência entre ambientes |

## 3. Arquitetura End-to-End

```
FONTES (PNCP) → DOCUMENT SCRAPER → Kafka(captured_documents) → PROCESSING ENGINE → MongoDB + Blob
                                    ↑
                 CONTROL CENTER (API 8080, Scheduler)
                                    ↓
                 Kafka(scraping_commands) → DOCUMENT SCRAPER
```

### Fluxo de Dados

1. **Cadastro de fontes** — Control Center cadastra `FonteCaptacao` (tipo AGREGADOR_GOV, canais com URL/instruções/palavras-chave/frequência).
2. **Agendamento** — `AgendamentoScheduler` (60s) publica `ExecutarScraping` no Kafka para fontes com `proxima_execucao <= now()`.
3. **Scraping** — Document Scraper consome `scraping_commands`, usa Playwright para navegar/baixar documentos (PDF/Excel/HTML), faz upload para Blob (staging), publica `DocumentoCapturado` em `captured_documents`.
4. **Processamento** — Processing Engine consome `captured_documents`, executa pipeline:
   - Download do blob → Extração de texto (PDFBox/POI) → Chunking → IA Provider Chain (fallback sequencial) → Persistência do Evento → ACK Kafka
5. **Sincronização SGA** — `sga-status-sync` (batch 8h) verifica eventos `NAO_VERIFICADO`/`INEDITO` via OAuth2 + API SGA.

### Pipeline de Processamento (7 steps)

| Step | Ação |
|---|---|
| 1. PersistDocumento | Salva documento capturado |
| 2. Extraction | Extração semântica via IA |
| 3. Enrichment | Enriquece resultado (`INVALID_AI_RESPONSE` → falha; `NO_EVENT` → descarta + deleta blob) |
| 4. Metrics | Salva métricas preliminares |
| 5. SgaVerification | Verifica status no SGA |
| 6. BlobPromotion | Move blob staging → produção |
| 7. EventPersistence | Persiste evento + resolve link da fonte |

Compensação: em qualquer exceção, se blob de produção foi criado, tenta deletar.

## 4. ecad-shared — Kernel

### Domínio

**Entidades:** `Evento` (aggregate principal — calcularStatus, calcularNivelCompletude, ehDuplicataDe, gerarCodigoEvento), `FonteCaptacao` (UUID v3 derivado do baseStoragePath), `Documento`, `Evidencia`, `CriterioExtracao`, `MetricaExecucaoIA`, `MetricaExecucaoOperacional`, `CanalDeScraping`.

**Enums:** `StatusEvento`, `StatusSGA` (NAO_VERIFICADO, INEDITO, JA_CADASTRADO), `NivelCompletude`, `TipoEvidencia`, `TipoFonte`, `ProviderIA`, etc.

### Contratos Kafka

- `Topics`: `SCRAPING_COMMANDS`, `CAPTURED_DOCUMENTS`
- `ExecutarScraping`: comando (URL, tipo, palavras-chave, staging path)
- `DocumentoCapturado`: resultado (URL origem, staging, hash, metadados)

### Infraestrutura Cloud-Agnostic

`RepositoryFactory` retorna implementação **Mongo** ou **Local** (filesystem) para cada repositório, baseado em `mongoClient != null`.

| Recurso | Local | Cloud |
|---|---|---|
| Persistência | `.localdev/data/` (JSON) | MongoDB / Cosmos DB |
| Blob Storage | `.localdev/blobs/` | Azure Blob / Azurite |
| Mensageria | `LocalMessageQueue` (arquivo) | Kafka / Event Hubs |

### Componentes de Infra

- `AiProviderSettings` — valida credenciais (rejeita `******`, `changeme`, < 10 chars)
- `SgaCredentialsProvider` — OAuth2 client_credentials com cache de token (TTL 1h)
- `BlobStorageFactory` — Azure Blob ou Local
- `KafkaMessagePublisher` — publicação Kafka
- `MetricsCollector` — coleta por step
- `EventFailureTracker` — quarentena e dead-letter
- `RetryPolicy`, `LruCache`, `SingleflightScheduler`

## 5. ecad-control-center — API e Orquestração (8080)

### Controllers

| Controller | Rota | Função |
|---|---|---|
| `FontesController` | `/api/fontes` | CRUD fontes, execução manual, lote PNCP (sínc/assínc) |
| `EventosController` | `/api/eventos` | Listagem/filtro de eventos |
| `ExportacaoController` | `/api/exportacao` | Geração XLSX, envio por e-mail |
| `MetricasController` | `/api/metricas` | Custos de IA |
| `HealthController` | `/health` | Health check agregado (local/cloud + SGA) |

### Serviços

- `AgendamentoScheduler` — scraping a cada `AGENDAMENTO_INTERVAL_MS` (default 60s), condicionado a `CONTROL_CENTER_SCHEDULING_ENABLED`.
- `PncpAsyncBatchJobService` — lote PNCP com virtual threads e tracking de progresso.
- `PncpContractSourcesBootstrap` — popula fontes/critérios no startup (apenas cloud).
- `EventPublisher` — `CloudEventPublisher` (Kafka) ou `LocalQueuePublisher` (fila local).

## 6. ecad-processing-engine — Worker IA (8081)

### Consumo

- Kafka consumer (`cg-processing-engine`) do tópico `captured_documents` com commit manual de offset.
- `CapturedDocumentHandler` — deserializa, delega ao pipeline, gerencia quarentena (payload inválido → dead-letter; falha transitória → retry; max tentativas → quarentena).

### Extração via IA

- `ExtractionService` — monta prompt (limite 50k chars), remove cercas markdown, interpreta JSON (`ExtractionResult`), cache por SHA-256.
- `HttpAiProviderChain` — itera `AI_PROVIDER_CHAIN` (openrouter → gemini_nativo → ollama → azure_openai) com **fallback automático**. Registra métricas (tokens, custo, latência) por chamada.
- `AiProviderSettings.isValidCredential()` — rejeita chaves mascaradas, garantindo que providers não configurados sejam pulados.

## 7. ecad-document-scraper — Worker de Scraping

- Kafka consumer (`cg-document-scraper`) do tópico `scraping_commands`.
- `HybridScrapingPipeline` — seleciona scraper method via `canHandle()`.
- `ContratoMusicalHybridScraperMethod` — scraping PNCP: normaliza palavras-chave, resolve URL de busca, descobre links, baixa PDF, persiste documento.
- `PlaywrightBrowserPool` — pool de instâncias Chromium headless com anti-bloqueio (stealth, user-agent rotation, delays humanos).
- Publica `DocumentoCapturado` no tópico `captured_documents`.

## 8. ecad-sga-status-sync — Sincronização SGA

- **Batch only** — não consome Kafka. Scheduler `@Scheduled(fixedDelay = 28_800_000)` (8h) + execução no startup.
- Carrega eventos `NAO_VERIFICADO` / `INEDITO`, processa em paralelo (pool 4 threads + semáforo).
- `SgaApiClient` — OAuth2 client_credentials, fuzzy matching (tokenSetRatio + Levenshtein) título + município + data.
- Cache LRU (5 min TTL, 10k entradas), retry com backoff exponencial (3 tentativas).

## 9. ecad-deduplicator — CLI

Ferramenta CLI standalone para mesclar eventos duplicados em arquivo JSON:

- **Blocking** — gera pares candidatos via hash, URL, título, cidade, data.
- **Regras determinísticas** — conflito de local, match exato, mesma evidência.
- **Heurística ponderada** — score 0-1 (título, local, data, intérpretes, promotor).
- **IA opcional** — provider chain para casos borderline.
- **Union-Find** — agrupa duplicatas; merge preserva informação mais rica.
- `--dry-run` para preview.

## 10. ecad-log-analyser — CLI

Gera planilha Excel a partir de dados de telemetria (métricas IA + operacionais). Carrega de `.localdev/data/` ou MongoDB.

## 11. Modelo de Dados (MongoDB)

### `fontes_captacao`

```json
{
  "_id": "UUID (v3 do baseStoragePath)",
  "nome": "Prefeitura de Salvador",
  "unidade_ecad": "BA",
  "base_storage_path": "BA/SALVADOR",
  "canais_scraping": [{
    "url": "https://...", "palavras_chaves_busca": ["contrato","musical"],
    "frequencia": {"tipo": "diario", "horario": "06:00"},
    "ativo": true, "tipo": "agregadorGov"
  }]
}
```

### `eventos`

```json
{
  "_id": "UUID", "codigo_evento": "2024-00001",
  "titulo": "Festival de Musica Local",
  "data_inicio": "2024-08-01T20:00:00-03:00",
  "municipio": "Salvador", "uf": "BA", "unidade_ecad": "BA",
  "promotor_cnpj": "...", "promotor_nome": "...",
  "interpretes": ["Banda X"], "tipo_musica": "aoVivo",
  "cobranca_ingresso": "sim", "status": "agendado",
  "status_sga": "inedito", "nivel_completude": "alto",
  "evidencias": [{"tipo": "contratoMusical", "hash_arquivo": "..."}]
}
```

### `documentos_capturados`

```json
{
  "_id": "UUID", "id_fonte_captacao": "UUID",
  "tipo_documento": "contratoMusical",
  "blob_path": "BA/SALVADOR/uuid/contrato.pdf",
  "hash_arquivo": "sha256...", "status": "processado",
  "id_evento_gerado": "UUID"
}
```

### `metricas_execucao_ia`

```json
{
  "componente": "processingEngine",
  "provider": "geminiNativo", "modelo_utilizado": "gemini-2.5-flash",
  "tokens_input": 1500, "tokens_output": 300,
  "custo_usd": 0.0015, "duracao_chamada_ms": 2500, "sucesso": true
}
```

## 12. Estratégias Técnicas

### IA Provider Chain

- Chain configurável via `AI_PROVIDER_CHAIN` (default: `gemini_nativo,openrouter,azure_openai`)
- Fallback automático: timeout, rate limit, erro 5xx, resposta inválida → tenta próximo
- Telemetria por chamada: tokens, custo USD, latência → `MetricaExecucaoIA`

### Idempotência e Deduplicação

- Hash SHA-256 no `DocumentoCapturado` evita reprocessar mesmo blob
- Upsert por `codigo_evento` + `unidade_ecad` evita duplicar eventos
- `ecad-deduplicator`: roda offline, fuzzy matching, mantém o mais completo

### Scraping Robusto

- Playwright headless com stealth, viewport random, user-agent rotation, delays humanos
- Wait strategies: networkidle, selector visível
- Rate limiting por fonte
- Screenshot on failure para debug

## 13. Docker Compose

| Perfil | Serviços |
|---|---|
| `infra` | MongoDB, Azurite, Kafka |
| `full` | Infra + serviços ECAD (control-center, processing-engine, document-scraper, sga-status-sync) |
| `tools` | deduplicator, log-analyser |

## 14. Portas e Endpoints

| Serviço | Porta | Health |
|---|---|---|
| Control Center | 8080 | `/actuator/health` |
| Processing Engine | 8081 | `/actuator/health` |
| MongoDB | 27017 | `db.adminCommand('ping')` |
| Kafka | 9092/9094 | `kafka-topics.sh --list` |
| Azurite | 10000 | HTTP |

## 15. Segurança

- Secrets em `.deploy/azure_app_settings.json` (gitignored), injetados como secrets do Container App.
- `run-azure.ps1` resolve connection strings de Cosmos DB, Storage e Event Hubs em runtime.
- Credenciais SGA: OAuth `client_credentials` (cache 1h) ou token estático.
- Rotação de chaves de IA: 90 dias.

## 16. Build e Testes

```powershell
.\mvndw.ps1 clean verify           # build + testes
.\mvndw.ps1 install -DskipTests   # build sem testes
```

Requisitos: Java ≥ 21, Maven ≥ 3.9 (via wrapper).

---

*Versão 3.0 — Documento canônico. Em caso de conflito, este documento e o código-fonte prevalecem.*
