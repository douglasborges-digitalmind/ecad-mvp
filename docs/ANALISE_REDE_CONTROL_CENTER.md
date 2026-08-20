# Análise: Restrições de Rede Afetando o Control Center no Azure

> Data: 2026-08-20
> Contexto: Control Center falha na inicialização em ambiente Azure com restrições de rede internas, enquanto funciona em ambiente aberto.

---

## 1. Síntese do Problema

O Control Center apresenta dois sintomas no ambiente Azure restrito:

1. **Erro de inicialização** relacionado à comunicação com o MongoDB (Cosmos DB for MongoDB).
2. **Erro de "probe 1"** — healthcheck do container falhando, causando reinícios em loop.

No ambiente aberto (sem restrições de rede), o mesmo deployment funciona corretamente.

---

## 2. Arquitetura de Comunicação

### Como o Control Center se comunica com o banco de dados

A comunicação entre o Control Center e o banco de dados é **direta**, não via Kafka:

```
Control Center ──direto──→ MongoDB/Cosmos DB (ler/gravar fontes, eventos, métricas)
Control Center ──Kafka──→ Document Scraper (comandos de scraping)
Document Scraper ──Kafka──→ Processing Engine (documentos capturados)
Processing Engine ──direto──→ MongoDB/Cosmos DB + Blob Storage
```

- O `ControlCenterCloudClients` cria um `MongoClient` síncrono via `MongoClientFactory.create(settings.mongoConnectionString())`.
- O `ControlCenterConfiguration` instancia um `RepositoryFactory` que recebe o `mongoClient` e cria repositórios (`FonteCaptacaoRepository`, `EventoRepository`, etc.).
- Esses repositórios fazem **acesso direto ao MongoDB** via driver síncrono do Mongo.
- O Kafka atua como **barramento de eventos assíncronos** entre os workers (scraper e processing engine), enquanto o MongoDB é acessado diretamente por cada serviço que precisa persistir ou ler dados.

---

## 3. A Connection String do Cosmos DB

O script `scripts/run-azure.ps1` (linha 114) constrói a connection string assim:

```
mongodb://<conta>:<chave>@<conta>.mongo.cosmos.azure.com:10255/?ssl=true&replicaSet=globaldb&retrywrites=false&maxIdleTimeMS=120000&appName=@<conta>@
```

**Dois detalhes críticos:**

1. **Porta 10255** — não é a porta padrão 27017 do MongoDB. É a porta específica do Cosmos DB for MongoDB.
2. **Endpoint público** — `<conta>.mongo.cosmos.azure.com` é um endpoint público da Azure.

---

## 4. Por que funciona no ambiente "aberto" mas falha no "restrito"

| Cenário | Ambiente aberto | Ambiente com restrições |
|---|---|---|
| **Cosmos DB firewall** | Aceita conexões de qualquer IP (`0.0.0.0`) | Pode estar configurado para aceitar apenas de IPs/subnets específicas |
| **NSG / Egress do Container Apps** | Sem restrição de saída | Pode bloquear tráfego de saída na porta **10255** |
| **VNet injection** | ACA Environment sem VNet customizada | ACA Environment injetado em VNet — egress controlado por NSG |
| **Service endpoints** | Não necessários (acesso público) | Se Cosmos DB exige VNet service endpoint, o ACA precisa ter `Microsoft.AzureCosmosDB` habilitado na subnet |
| **Private endpoints** | Não usa | Se o Cosmos DB tem private endpoint, o DNS `<conta>.mongo.cosmos.azure.com` precisa resolver para o IP interno |
| **DNS resolution** | DNS público padrão | Pode usar DNS zones customizadas que não resolvem o endpoint público |

---

## 5. O Efeito em Cascata no Control Center

```
Container App inicia
  ↓
ControlCenterSettings.validate() → OK (só checa se string NÃO é vazia)
  ↓
MongoClientFactory.create() → cria MongoClient (LAZY — não conecta ainda)
  ↓
Spring context sobe aparentemente OK
  ↓
PncpContractSourcesBootstrap (ApplicationRunner) executa no startup
  ↓
Tenta acessar Cosmos DB → TIMEOUT / CONNECTION REFUSED (porta 10255 bloqueada)
  ↓
Aplicação entra em estado degradado ou falha
  ↓
Healthcheck probe 1 falha → container reinicia em loop
```

---

## 6. Por que o erro se manifesta como "probe 1"

O Dockerfile do Control Center tem:

```dockerfile
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1
```

- `start-period=40s` — o Spring Boot com `PncpContractSourcesBootstrap` tentando conectar ao Cosmos DB pode demorar mais que 40s para responder (timeout de conexão TCP + retries do driver Mongo).
- Se o `/actuator/health` não responde a tempo → probe falha → após 3 retries (90s) o container é marcado como unhealthy e reinicia.

---

## 7. Problema Adicional: `MongoHealthIndicator` não registrado no Control Center

### Comparação entre módulos

**ProcessingEngineConfiguration** (registra health indicators):

```java
@Bean
@ConditionalOnExpression("'${LOCAL_DEVELOPMENT_ENABLED:true}'.equals('false')")
MongoHealthIndicator mongoHealthIndicator(...) { ... }

@Bean
@ConditionalOnExpression("'${LOCAL_DEVELOPMENT_ENABLED:true}'.equals('false')")
BlobHealthIndicator blobHealthIndicator(...) { ... }
```

**ControlCenterConfiguration** — **NÃO registra nenhum dos dois**. Isso significa que o `/actuator/health` do Control Center não tem um indicador real de Mongo, apenas o fallback genérico do Spring Boot.

### Consequência

O `HealthController` customizado (que responde em `/health` e `/api/health`) delega para `healthEndpoint.health()`:

```java
var health = healthEndpoint.health();
var isHealthy = Status.UP.equals(health.getStatus());
services.put("cosmos_db", new ServiceHealthResult(isHealthy, ...));
```

Como o `MongoHealthIndicator` **não está registrado** no contexto do Control Center, o `healthEndpoint.health()` não verifica o Mongo de fato, dificultando o diagnóstico.

---

## 8. Como Confirmar a Causa

Execute no ambiente restrito:

```powershell
# 1. Verificar se o Cosmos DB permite acesso do Container Apps
az cosmosdb show --name <cosmosAccount> --resource-group <rg> --query "ipRules"

# 2. Verificar se há VNet no ACA Environment
az containerapp env show --name <acaEnv> --resource-group <rg> --query "properties.vnetConfiguration"

# 3. Verificar logs do control-center
az containerapp logs show --name control-center-<suffix> --resource-group <rg> --follow

# 4. Testar conectividade de dentro do container
az containerapp exec --name control-center-<suffix> --resource-group <rg> --command "curl -v telnet://<cosmosAccount>.mongo.cosmos.azure.com:10255"
```

---

## 9. Correções Possíveis

### Opção A — Liberar acesso no Cosmos DB (mais simples)

```powershell
# Adicionar o IP/subnet do ACA Environment nas regras de firewall do Cosmos DB
az cosmosdb update --name <cosmosAccount> --resource-group <rg> `
  --ip-range-filter "<subnet-do-aca-environment>"
```

### Opção B — Habilitar service endpoint na subnet do ACA

```powershell
az network vnet subnet update --name <subnet> --vnet-name <vnet> `
  --resource-group <rg> --service-endpoints Microsoft.AzureCosmosDB
```

### Opção C — Usar private endpoint (mais seguro, se o ambiente exige)

- Criar private endpoint para o Cosmos DB
- Configurar DNS zone privada para resolver `<conta>.mongo.cosmos.azure.com` → IP interno

### Opção D — Ajustar o `start-period` do healthcheck (mitigação)

- Aumentar para `120s` no Dockerfile, dando mais tempo para o bootstrap tentar conectar antes do probe falhar

### Opção E — Registrar `MongoHealthIndicator` no Control Center (diagnóstico)

Adicionar ao `ControlCenterConfiguration.java`:

```java
@Bean
@org.springframework.boot.autoconfigure.condition.ConditionalOnExpression("'${LOCAL_DEVELOPMENT_ENABLED:true}'.equals('false')")
MongoHealthIndicator mongoHealthIndicator(ControlCenterCloudClients cloudClients, ControlCenterSettings settings) {
    return new MongoHealthIndicator(cloudClients.mongoClient(), settings.mongoDatabaseName());
}
```

---

## 10. Recomendação

O problema **não é o código Java**, é a configuração de rede do ambiente restrito. A correção imediata é verificar as regras de firewall do Cosmos DB e/ou NSG do ACA Environment.

Ações recomendadas em ordem de prioridade:

1. **Verificar regras de firewall do Cosmos DB** e NSG do ACA Environment (causa raiz).
2. **Aumentar o `start-period`** do healthcheck no Dockerfile (mitigação rápida).
3. **Adicionar o `MongoHealthIndicator`** no Control Center (para diagnóstico mais claro nos logs).
4. **Adicionar log detalhado no `PncpContractSourcesBootstrap`** para capturar o erro exato de conexão.

---

## 11. Referências no Código

| Arquivo | Relevância |
|---|---|
| `scripts/run-azure.ps1` (linha 114) | Construção da connection string do Cosmos DB |
| `ecad-control-center/Dockerfile` | Healthcheck com `start-period=40s` |
| `ecad-control-center/src/main/java/.../ControlCenterCloudClients.java` | Criação do `MongoClient` (lazy) |
| `ecad-control-center/src/main/java/.../ControlCenterSettings.java` | `validate()` — falha se `MONGODB_CONNECTION_STRING` vazia |
| `ecad-control-center/src/main/java/.../ControlCenterConfiguration.java` | Não registra `MongoHealthIndicator` |
| `ecad-control-center/src/main/java/.../HealthController.java` | Delega para `healthEndpoint.health()` |
| `ecad-shared/src/main/java/.../health/MongoHealthIndicator.java` | Health check do MongoDB (ping) |
| `ecad-shared/src/main/java/.../mongodb/MongoClientFactory.java` | Factory do `MongoClient` com codec registry |
| `ecad-processing-engine/src/main/java/.../ProcessingEngineConfiguration.java` | Referência: registra `MongoHealthIndicator` e `BlobHealthIndicator` |
