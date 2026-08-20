<#
.SYNOPSIS
    Provisiona infraestrutura Azure e sobe os contêineres ECAD como Container Apps.
.DESCRIPTION
    Lê .deploy/azure-resources.json para nomenclatura e .deploy/azure_app_settings.json
    (ou .dev.json) para variáveis de ambiente. Substitui o run-local.ps1 para nuvem.
    Pré-requisitos:
      - Azure CLI (az) instalado e autenticado (az login)
      - Docker rodando (para build/push de imagens ao ACR)
      - Extensão containerapp (az extension add --name containerapp)

    Modos:
      infra  -> provisiona Cosmos DB, Storage, Event Hubs, ACR, Log Analytics, Container Apps Env.
      apps   -> builda imagens Docker, push ao ACR e cria/atualiza Container Apps (assume infra OK).
      all    -> infra + apps.
      down   -> remove os Container Apps (mantém a infra persistente).

    Containers e Event Hubs declarados em azure-resources.json são criados na etapa de infra.
    As envs de azure_app_settings[s.dev].json são aplicadas aos Container Apps na etapa de apps.

    Mapeamento de serviços (docker-compose.yml -> Container Apps):
      Container App names tem limite de 32 chars; usamos <service>-<suffix>
      control-center       -> control-center-<suffix>
      processing-engine    -> processing-engine-<suffix>
      document-scraper     -> document-scraper-<suffix>
      sga-status-sync      -> sga-status-sync-<suffix>
.PARAMETER Mode
    infra, apps, all, down
.PARAMETER Slot
    dev (usa azure_app_settings.dev.json) | prod (usa azure_app_settings.json)
.PARAMETER Service
    Nome do serviço específico (apenas modo apps). Ex.: control-center
.PARAMETER SkipBuild
    Pula o build/push e apenas atualiza a configuração do Container App existente.
.EXAMPLE
    .\scripts\run-azure.ps1 -Mode infra
    .\scripts\run-azure.ps1 -Mode apps
    .\scripts\run-azure.ps1 -Mode all
    .\scripts\run-azure.ps1 -Mode apps -Service control-center
    .\scripts\run-azure.ps1 -Mode down
.NOTES
    Recursos são idempotentes (az create-if-not-exists). Secrets com ****** no JSON
    devem ser preenchidos manualmente via Key Vault antes de executar 'apps'.
#>
param(
    [Parameter(Position = 0)]
    [ValidateSet("infra", "apps", "all", "down")]
    [string]$Mode = "all",

    [ValidateSet("dev", "prod")]
    [string]$Slot = "dev",

    [string]$Service,

    [switch]$SkipBuild
)
$ErrorActionPreference = "Stop"
$root = (Resolve-Path "$PSScriptRoot\..").Path
$resourcesFile = Join-Path $root ".deploy\azure-resources.json"
$envFile = if ($Slot -eq "dev") {
    Join-Path $root ".deploy\azure_app_settings.dev.json"
} else {
    Join-Path $root ".deploy\azure_app_settings.json"
}

# --- Helpers de output (mesmo estilo do run-local.ps1) ---
function Write-Step([string]$msg) { Write-Host "`n[STEP] $msg" -ForegroundColor Yellow }
function Write-Ok([string]$msg)   { Write-Host "  [OK] $msg" -ForegroundColor Green }
function Write-Warn([string]$msg) { Write-Host "  [!] $msg" -ForegroundColor Yellow }
function Write-Err([string]$msg)  { Write-Host "  [X] $msg" -ForegroundColor Red }

trap {
    Write-Err "Falha inesperada: $_"
    break
}

# Carrega JSONs de configuração
if (-not (Test-Path $resourcesFile)) { Write-Err "Arquivo nao encontrado: $resourcesFile"; exit 1 }
if (-not (Test-Path $envFile))        { Write-Err "Arquivo nao encontrado: $envFile"; exit 1 }
$cfg = Get-Content $resourcesFile -Raw | ConvertFrom-Json
$script:appSettings = @(Get-Content $envFile -Raw | ConvertFrom-Json)

$rg        = $cfg.resourceGroup
$location  = $cfg.location
$suffix    = $cfg.nameSuffix
$prefix    = $cfg.namePrefix
$acrName   = $cfg.containerRegistryName
$acaEnv    = $cfg.containerAppsEnvironment
$cosmosAcct= $cfg.cosmosAccountName
$cosmosDb  = $cfg.cosmosDatabaseName
$storageAcct = $cfg.storageAccountName
$ehNamespace = $cfg.eventHubsNamespace
$lawName   = $cfg.logAnalyticsWorkspace

# Serviços ECAD que viram Container Apps (mesma lista de perfis 'full' do compose)
$ecadServices = @(
    @{ Name = "control-center";    Port = 8080; Ingress = $true },
    @{ Name = "processing-engine"; Port = 8080; Ingress = $false },
    @{ Name = "document-scraper";  Port = 8080; Ingress = $false },
    @{ Name = "sga-status-sync";   Port = 8080; Ingress = $false }
)

# --- Resolve connection strings reais dos recursos Azure provisionados.
# Consulta o Azure para obter keys/connection strings reais e injeta as
# env vars necessárias pelos serviços Java (KAFKA_*, MONGODB_*, AZURE_STORAGE_*).
function Resolve-AzureConnectionStrings {
    Write-Step "Resolvendo connection strings do Azure..."

    # Cosmos DB (MongoDB API): constrói connection string mongodb:// a partir da primary key
    # (az cosmosdb keys list --type connection-strings mascara com ******)
    $cosmosKey = az cosmosdb keys list --name $cosmosAcct --resource-group $rg `
        --query "primaryMasterKey" -o tsv 2>$null
    if (-not $cosmosKey) { Write-Err "Falha ao obter key do Cosmos DB '$cosmosAcct'"; exit 1 }
    $cosmosConn = "mongodb://$cosmosAcct`:$cosmosKey@$cosmosAcct.mongo.cosmos.azure.com:10255/?ssl=true&replicaSet=globaldb&retrywrites=false&maxIdleTimeMS=120000&appName=@$cosmosAcct@"
    Write-Ok "Cosmos DB (MongoDB API): $cosmosAcct / $cosmosDb"

    # Storage Account: connection string com chave primária
    $stgKey = az storage account keys list --account-name $storageAcct --resource-group $rg `
        --query "[0].value" -o tsv 2>$null
    if (-not $stgKey) { Write-Err "Falha ao obter key do Storage '$storageAcct'"; exit 1 }
    $stgConn = "DefaultEndpointsProtocol=https;AccountName=$storageAcct;AccountKey=$stgKey;EndpointSuffix=core.windows.net"
    Write-Ok "Storage: $storageAcct"

    # Event Hubs: connection string do RootManageSharedAccessKey
    $ehConn = az eventhubs namespace authorization-rule keys list `
        --resource-group $rg --namespace-name $ehNamespace `
        --name "RootManageSharedAccessKey" --query "primaryConnectionString" -o tsv 2>$null
    if (-not $ehConn) { Write-Err "Falha ao obter connection string do Event Hubs '$ehNamespace'"; exit 1 }
    Write-Ok "Event Hubs: $ehNamespace"

    # Extrai o FQDN do broker Kafka do Event Hubs para KAFKA_BOOTSTRAP_SERVERS
    # Formato: Endpoint=sb://<namespace>.servicebus.windows.net/;SharedAccessKeyName=...;SharedAccessKey=...
    $ehFqdn = ($ehConn -split ';' | Where-Object { $_ -match 'Endpoint=' }) -replace 'Endpoint=sb://','' -replace '/',''
    $ehKeyName = ($ehConn -split ';' | Where-Object { $_ -match 'SharedAccessKeyName=' }) -replace 'SharedAccessKeyName=',''
    $ehAccessKey = ($ehConn -split ';' | Where-Object { $_ -match 'SharedAccessKey=' }) -replace 'SharedAccessKey=',''
    $kafkaBootstrap = "$ehFqdn`:9093"
    $saslJaasConfig = "org.apache.kafka.common.security.plain.PlainLoginModule required username=`"$ehKeyName`" password=`"$ehAccessKey`";"
    # KAFKA_SASL_USERNAME e KAFKA_SASL_PASSWORD separados 
    # evitam corrupcao de aspas pelo Azure Container Apps ao armazenar o JAAS config como env var
    # Event Hubs Kafka protocolo exige username="$ConnectionString" e password=<connection string completa>
    $saslUsername = '$ConnectionString'
    $saslPassword = $ehConn

    # Nomes dos Event Hubs e consumer groups do azure-resources.json
    $ehScraping = $cfg.eventHubs | Where-Object { $_ -match "scraping" } | Select-Object -First 1
    $ehCaptured = $cfg.eventHubs | Where-Object { $_ -match "captured" } | Select-Object -First 1
    Write-Ok "Event Hubs topics: $ehScraping / $ehCaptured"

    # Blob container name do azure-resources.json
    $blobContainer = $cfg.blobContainers | Select-Object -First 1

    # Constrói dicionário de env vars resolvidas do Azure.
    # Inclui apenas os nomes canônicos (KAFKA_*/MONGODB_*/AZURE_STORAGE_*) usados
    # pelos módulos Java.
    $resolved = @{
        # Modo cloud — obrigatório para que o bootstrap rode
        "LOCAL_DEVELOPMENT_ENABLED" = "false"
        "LOCAL_DEVELOPMENT_ROOT"    = "/tmp/ecad-localdev"

        # Kafka / Event Hubs (protocolo Kafka sobre Event Hubs)
        "KAFKA_BOOTSTRAP_SERVERS"          = $kafkaBootstrap
        "KAFKA_SECURITY_PROTOCOL"          = "SASL_SSL"
        "KAFKA_SASL_MECHANISM"             = "PLAIN"
        "KAFKA_SASL_JAAS_CONFIG"           = ""  # vazio: força resolver via KAFKA_SASL_USERNAME/PASSWORD
        "KAFKA_SASL_USERNAME"              = $saslUsername
        "KAFKA_SASL_PASSWORD"              = $saslPassword
        "KAFKA_SCRAPING_COMMANDS_TOPIC"    = $ehScraping
        "KAFKA_CAPTURED_DOCUMENTS_TOPIC"   = $ehCaptured
        # KAFKA_CONSUMER_GROUP não é injetado — cada serviço usa seu próprio default:
        #   document-scraper -> cg-document-scraper
        #   processing-engine -> cg-processing-engine

        # MongoDB / Cosmos DB
        "MONGODB_CONNECTION_STRING"  = $cosmosConn
        "MONGODB_DATABASE_NAME"      = $cosmosDb

        # Azure Storage / Blob
        "AZURE_STORAGE_CONNECTION_STRING" = $stgConn
        "AZURE_BLOB_CONTAINER_NAME"       = $blobContainer
    }

    # Sobrescreve valores do JSON com os valores resolvidos do Azure
    foreach ($s in $script:appSettings) {
        if ($resolved.ContainsKey($s.name)) {
            $s.value = $resolved[$s.name]
        }
    }

    # Adiciona env vars que não existem no JSON mas são necessárias para os serviços Java
    $existingNames = @($script:appSettings | ForEach-Object { $_.name })
    foreach ($entry in $resolved.GetEnumerator()) {
        if ($entry.Key -notin $existingNames) {
            $script:appSettings = @($script:appSettings) + [PSCustomObject]@{ name = $entry.Key; value = $entry.Value; slotSetting = $false }
            Write-Warn "Env var ausente no JSON — injetada: $($entry.Key)"
        }
    }
}

# --- Gera YAML completo para az containerapp create --yaml
# O parâmetro --env-vars do Azure CLI não suporta múltiplos --env-vars
# (sobrescreve em vez de acumular) e connection strings com ; = + quebram
# o parser quando passadas como espaço-separadas. A solução é usar --yaml
# com um arquivo de configuração completo.
function Write-ContainerAppYaml {
    param(
        [string]$Path,
        [string]$ContainerName,
        [string]$ImageName,
        [int]$TargetPort,
        [double]$Cpu = 1.0,
        [string]$Memory = "2.0Gi",
        [int]$MinReplicas = 1,
        [int]$MaxReplicas = 2,
        [bool]$IngressEnabled = $false,
        [string]$IngressType = "internal",
        [string]$RegistryServer = "",
        [string]$RegistryUsername = "",
        [string]$RegistryPassword = ""
    )

    $yaml = [System.Collections.ArrayList]@()
    [void]$yaml.Add("properties:")
    [void]$yaml.Add("  environmentId: $script:acaEnvId")
    if ($RegistryServer) {
        [void]$yaml.Add("  registries:")
        [void]$yaml.Add("    - server: $RegistryServer")
        [void]$yaml.Add("      username: $RegistryUsername")
        [void]$yaml.Add("      passwordSecretRef: acrpassword")
    }
    if ($IngressEnabled) {
        [void]$yaml.Add("  configuration:")
        [void]$yaml.Add("    ingress:")
        [void]$yaml.Add("      external: $(if ($IngressType -eq 'external') { 'true' } else { 'false' })")
        [void]$yaml.Add("      targetPort: $TargetPort")
        [void]$yaml.Add("      transport: http")
    }
    [void]$yaml.Add("  template:")
    [void]$yaml.Add("    containers:")
    [void]$yaml.Add("      - name: $ContainerName")
    [void]$yaml.Add("        image: $ImageName")
    [void]$yaml.Add("        resources:")
    [void]$yaml.Add("          cpu: '$Cpu'")
    [void]$yaml.Add("          memory: $Memory")
    [void]$yaml.Add("        env:")
    # Variaveis que devem ser armazenadas como secrets (contem chaves/credentials)
    # Azure Container Apps mascara valores com padrao de chave como ******, corrompendo-os
    $secretNames = @(
        "KAFKA_SASL_PASSWORD",
        "MONGODB_CONNECTION_STRING",
        "AZURE_STORAGE_CONNECTION_STRING",
        "OPENROUTER_API_KEY", "GEMINI_API_KEY", "AZURE_OPENAI_API_KEY"
    )

    # Garantir que a lista de secrets no YAML so tenha entradas unicas
    $yamlSecrets = [System.Collections.ArrayList]@()
    if ($RegistryServer) {
        [void]$yamlSecrets.Add("    - name: acrpassword")
        [void]$yamlSecrets.Add("      value: $RegistryPassword")
    }

    foreach ($s in $script:appSettings) {
        $name = $s.name
        $val = $s.value
        if ($secretNames -contains $name) {
            if ($null -ne $val -and $val -ne "") {
                # Mapear nome da env var para nome do secret (hifen em vez de underscore)
                $secretName = ($name -replace '_','-').ToLower()
                [void]$yaml.Add("          - name: `"$name`"")
                [void]$yaml.Add("            secretRef: $secretName")
                [void]$yamlSecrets.Add("    - name: $secretName")
                [void]$yamlSecrets.Add("      value: $val")
            } else {
                [void]$yaml.Add("          - name: `"$name`"")
                [void]$yaml.Add("            value: ''")
            }
        } else {
            # YAML single-quoted strings: only need to double single quotes ('' -> ')
            $val = $val -replace "'","''"
            [void]$yaml.Add("          - name: `"$name`"")
            [void]$yaml.Add("            value: '$val'")
        }
    }
    [void]$yaml.Add("    scale:")
    [void]$yaml.Add("      minReplicas: $MinReplicas")
    [void]$yaml.Add("      maxReplicas: $MaxReplicas")

    if ($yamlSecrets.Count -gt 0) {
        [void]$yaml.Add("  secrets:")
        foreach ($line in $yamlSecrets) {
            [void]$yaml.Add($line)
        }
    }

    [System.IO.File]::WriteAllLines($Path, $yaml, [System.Text.UTF8Encoding]::new($false))
}

function Test-Prerequisites {
    Write-Step "Validando pre-requisitos..."
    $missing = @()
    if (-not (Get-Command az -ErrorAction SilentlyContinue)) { $missing += "Azure CLI (az)" }
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { $missing += "Docker" }
    if ($missing.Count -gt 0) { Write-Err "Pre-requisitos ausentes: $($missing -join ', ')"; exit 1 }

    # Verifica login no Azure
    $acct = az account show --query "name" -o tsv 2>$null
    if ($LASTEXITCODE -ne 0) { Write-Err "Nao autenticado no Azure. Execute: az login"; exit 1 }
    Write-Ok "Autenticado na subscription: $acct"

    # Garante que a extensao containerapp esta instalada
    $ext = az extension list --query "[?name=='containerapp'].name" -o tsv 2>$null
    if (-not $ext) {
        Write-Host "  Instalando extensao containerapp..." -ForegroundColor Yellow
        az extension add --name containerapp --yes 2>$null
        if ($LASTEXITCODE -ne 0) { Write-Err "Falha ao instalar extensao containerapp"; exit 1 }
    }
    Write-Ok "Extensao containerapp OK"

    # Docker daemon
    try { docker version --format '{{.Server.Version}}' | Out-Null } catch {
        Write-Err "Docker daemon nao esta rodando."; exit 1
    }
    Write-Ok "Docker OK"
}

# =====================================================================
# MODO INFRA: provisiona recursos Azure gerenciados
# =====================================================================
function Start-Infrastructure {
    Write-Step "Criando resource group '$rg' (regiao: $location)..."
    az group create --name $rg --location $location --query "properties.provisioningState" -o tsv 2>$null | Out-Null
    Write-Ok "Resource group OK"

    # --- Log Analytics Workspace (necessário para Container Apps Environment) ---
    Write-Step "Criando Log Analytics Workspace '$lawName'..."
    az monitor log-analytics workspace create `
        --resource-group $rg --workspace-name $lawName --location $location 2>$null | Out-Null
    $lawCustomerId = az monitor log-analytics workspace show `
        --resource-group $rg --workspace-name $lawName --query "customerId" -o tsv 2>$null
    $lawKey = az monitor log-analytics workspace get-shared-keys `
        --resource-group $rg --workspace-name $lawName --query "primarySharedKey" -o tsv 2>$null
    Write-Ok "Log Analytics OK (customerId: $lawCustomerId)"

    # --- Azure Container Registry ---
    Write-Step "Criando Container Registry '$acrName'..."
    az acr create --name $acrName --resource-group $rg --sku Basic --location $location 2>$null | Out-Null
    Write-Ok "ACR OK"

    # --- Cosmos DB com API MongoDB (driver MongoDB exige mongodb://...) ---
    Write-Step "Criando Cosmos DB account (MongoDB API) '$cosmosAcct'..."
    az cosmosdb create --name $cosmosAcct --resource-group $rg `
        --locations regionName=$location --kind MongoDB `
        --server-version 4.2 --default-consistency-level Session 2>$null | Out-Null
    Write-Ok "Cosmos DB account (MongoDB API) OK"

    Write-Host "  Criando database '$cosmosDb'..."
    az cosmosdb mongodb database create --account-name $cosmosAcct --resource-group $rg `
        --name $cosmosDb 2>$null | Out-Null
    Write-Ok "Database '$cosmosDb' OK"

    Write-Host "  Criando collections Cosmos (MongoDB API)..."
    foreach ($c in $cfg.cosmosContainers) {
        # Collection = name, shardKey = partitionKey sem a barra inicial
        $shardKey = $c.PartitionKey -replace '^/', ''
        az cosmosdb mongodb collection create --account-name $cosmosAcct --resource-group $rg `
            --database-name $cosmosDb --name $c.Name --shard $shardKey 2>$null | Out-Null
        Write-Ok "Collection '$($c.Name)' OK (shardKey: $shardKey)"
    }

    # --- Azure Storage (substitui Azurite local) ---
    Write-Step "Criando Storage Account '$storageAcct'..."
    $storageConn = az storage account create --name $storageAcct --resource-group $rg `
        --location $location --sku Standard_LRS --kind StorageV2 --query "provisioningState" -o tsv 2>$null
    Write-Ok "Storage Account OK"

    Write-Host "  Criando blob containers..."
    $stgKey = az storage account keys list --account-name $storageAcct --resource-group $rg `
        --query "[0].value" -o tsv 2>$null
    foreach ($bc in $cfg.blobContainers) {
        az storage container create --name $bc --account-name $storageAcct --account-key $stgKey 2>$null | Out-Null
        Write-Ok "Blob container '$bc' OK"
    }

    # --- Azure Event Hubs (substitui Kafka local) ---
    Write-Step "Criando Event Hubs namespace '$ehNamespace'..."
    az eventhubs namespace create --name $ehNamespace --resource-group $rg `
        --location $location --sku Standard 2>$null | Out-Null
    Write-Ok "Event Hubs namespace OK"

    Write-Host "  Criando Event Hubs..."
    foreach ($eh in $cfg.eventHubs) {
        az eventhubs eventhub create --name $eh --namespace-name $ehNamespace `
            --resource-group $rg --message-retention-in-days 1 --partition-count 3 2>$null | Out-Null
        Write-Ok "Event Hub '$eh' OK"
    }

    Write-Host "  Criando consumer groups..."
    foreach ($cg in $cfg.consumerGroups) {
        az eventhubs eventhub consumer-group create --name $cg.Name `
            --eventhub-name $cg.EventHub --namespace-name $ehNamespace --resource-group $rg 2>$null | Out-Null
        Write-Ok "Consumer group '$($cg.Name)' em '$($cg.EventHub)' OK"
    }

    # --- Container Apps Environment ---
    Write-Step "Criando Container Apps Environment '$acaEnv'..."
    az containerapp env create --name $acaEnv --resource-group $rg --location $location `
        --logs-workspace-id $lawCustomerId --logs-workspace-key $lawKey 2>$null | Out-Null
    Write-Ok "Container Apps Environment OK"

    Write-Host "`n=== Infraestrutura Azure Pronta ===" -ForegroundColor Green
    Write-Host "  Resource Group:       $rg" -ForegroundColor Gray
    Write-Host "  Cosmos DB:            $cosmosAcct / $cosmosDb" -ForegroundColor Gray
    Write-Host "  Storage Account:      $storageAcct" -ForegroundColor Gray
    Write-Host "  Event Hubs Namespace: $ehNamespace" -ForegroundColor Gray
    Write-Host "  Container Registry:   $acrName" -ForegroundColor Gray
    Write-Host "  ACA Environment:      $acaEnv" -ForegroundColor Gray
}

# =====================================================================
# MODO APPS: build, push e deploy dos contêineres ECAD
# =====================================================================
function Deploy-Apps {
    $services = if ($Service) { $ecadServices | Where-Object { $_.Name -eq $Service } } else { $ecadServices }
    if ($services.Count -eq 0) { Write-Err "Servico '$Service' nao encontrado."; exit 1 }

    $acrLoginServer = az acr show --name $acrName --resource-group $rg --query "loginServer" -o tsv 2>$null
    if (-not $acrLoginServer) { Write-Err "ACR '$acrName' nao encontrado. Execute -Mode infra primeiro."; exit 1 }

    # Habilita admin user no ACR para que o Container App possa fazer pull
    az acr update --name $acrName --resource-group $rg --admin-enabled true 2>$null | Out-Null
    $acrUsername = $acrName
    $acrPassword = az acr credential show --name $acrName --resource-group $rg --query "passwords[0].value" -o tsv 2>$null
    if (-not $acrPassword) { Write-Err "Falha ao obter credenciais do ACR"; exit 1 }
    Write-Ok "Credenciais ACR OK"

    # Resolve connection strings reais do Azure (sobrescreve valores hardcoded do JSON)
    Resolve-AzureConnectionStrings

    # Login no ACR (Docker)
    if (-not $SkipBuild) {
        Write-Step "Autenticando Docker no ACR ($acrLoginServer)..."
        az acr login --name $acrName 2>$null
        if ($LASTEXITCODE -ne 0) { Write-Err "Falha no login do ACR"; exit 1 }
        Write-Ok "Login ACR OK"
    }

    # Resolve o resource ID completo do ACA Environment (necessário para --yaml)
    $script:acaEnvId = az containerapp env show --name $acaEnv --resource-group $rg --query "id" -o tsv 2>$null
    if (-not $script:acaEnvId) { Write-Err "Falha ao obter ID do ACA Environment '$acaEnv'"; exit 1 }

    foreach ($svc in $services) {
        # Nome do Container App: <service>-<suffix> (limite 32 chars do Azure)
        $appName = "$($svc.Name)-$suffix"
        $imageName = "$acrLoginServer/$($svc.Name):latest"
        $moduleDir = Join-Path $root "ecad-$($svc.Name)"

        Write-Step "Processando servico: $($svc.Name) -> $appName"

        if (-not $SkipBuild) {
            Write-Host "  Buildando imagem Docker..."
            $dockerfilePath = Join-Path $moduleDir "Dockerfile"
            if (-not (Test-Path $dockerfilePath)) {
                Write-Warn "Dockerfile nao encontrado em $moduleDir — pulando."
                continue
            }
            docker build -t $imageName -f $dockerfilePath $root
            if ($LASTEXITCODE -ne 0) { Write-Err "Falha no build de $($svc.Name)"; exit 1 }
            Write-Ok "Build OK"

            Write-Host "  Enviando imagem ao ACR..."
            docker push $imageName
            if ($LASTEXITCODE -ne 0) { Write-Err "Falha no push de $($svc.Name)"; exit 1 }
            Write-Ok "Push OK"
        }

        # Constrói YAML com env vars (template.containers[].env + config base)
        # Usado para az containerapp update --yaml
        $yamlFile = Join-Path $env:TEMP "$appName-config.yaml"
        $ingressType = if ($svc.Ingress) { "external" } else { "internal" }
        Write-ContainerAppYaml -Path $yamlFile `
            -ContainerName $appName -ImageName $imageName `
            -TargetPort $svc.Port `
            -IngressEnabled $true -IngressType $ingressType `
            -RegistryServer $acrLoginServer `
            -RegistryUsername $acrUsername `
            -RegistryPassword $acrPassword

        # Verifica se o Container App já existe
        $exists = az containerapp show --name $appName --resource-group $rg --query "name" -o tsv 2>$null

        if (-not $exists) {
            # Cria o Container App sem env vars (env vars serão aplicadas via update)
            Write-Host "  Criando Container App..."
            az containerapp create --name $appName --resource-group $rg `
                --environment $acaEnv --image $imageName `
                --registry-server $acrLoginServer `
                --registry-username $acrUsername --registry-password $acrPassword `
                --cpu 1.0 --memory 2.0Gi --min-replicas 1 --max-replicas 2 `
                --ingress $ingressType --target-port $svc.Port 2>&1 | Out-Null
            if ($LASTEXITCODE -ne 0) {
                Write-Err "Falha ao criar $appName"
                exit 1
            }
            Write-Ok "Container App criado"
        } else {
            Write-Ok "Container App ja existe — atualizando imagem..."
            az containerapp update --name $appName --resource-group $rg `
                --image $imageName 2>&1 | Out-Null
            if ($LASTEXITCODE -ne 0) {
                Write-Err "Falha ao atualizar imagem de $appName"
                exit 1
            }
        }

        # Aplica env vars via az containerapp update --yaml
        # (--env-vars na CLI não suporta connection strings com ; = +)
        Write-Host "  Aplicando env vars..."

        # Primeiro, define os secrets no Container App
        # (necessario antes do --yaml com secretRef)
        $secretNames = @(
            "KAFKA_SASL_PASSWORD",
            "MONGODB_CONNECTION_STRING",
            "AZURE_STORAGE_CONNECTION_STRING",
            "OPENROUTER_API_KEY", "GEMINI_API_KEY", "AZURE_OPENAI_API_KEY"
        )
        foreach ($sname in $secretNames) {
            $sval = ($script:appSettings | Where-Object { $_.name -eq $sname } | Select-Object -First 1).value
            if ($null -ne $sval -and $sval -ne "") {
                $secName = ($sname -replace '_','-').ToLower()
                # Set secrets individually to avoid & ; = parsing issues
                & az containerapp secret set --name $appName --resource-group $rg --secrets "$secName=$sval" 2>&1 | Out-Null
            }
        }

        az containerapp update --name $appName --resource-group $rg `
            --yaml $yamlFile 2>&1 | Out-Null
        if ($LASTEXITCODE -ne 0) {
            Write-Err "Falha ao aplicar env vars em $appName"
            Write-Host "  YAML: $yamlFile" -ForegroundColor Gray
            exit 1
        }
        Write-Ok "Env vars aplicadas ($($script:appSettings.Count) vars)"

        Remove-Item $yamlFile -Force -ErrorAction SilentlyContinue

        # Exibe URL de ingress se aplicável
        if ($svc.Ingress) {
            $fqdn = az containerapp show --name $appName --resource-group $rg `
                --query "properties.configuration.ingress.fqdn" -o tsv 2>$null
            if ($fqdn) { Write-Host "  URL: https://$fqdn" -ForegroundColor Cyan }
        }
    }

    Write-Host "`n=== Deploy de Aplicacoes Concluido ===" -ForegroundColor Green
}

# =====================================================================
# MODO DOWN: remove Container Apps (mantém infra)
# =====================================================================
function Stop-Apps {
    Write-Step "Removendo Container Apps..."
    foreach ($svc in $ecadServices) {
        $appName = "$($svc.Name)-$suffix"
        $exists = az containerapp show --name $appName --resource-group $rg --query "name" -o tsv 2>$null
        if ($exists) {
            az containerapp delete --name $appName --resource-group $rg --yes 2>$null | Out-Null
            Write-Ok "Removido: $appName"
        } else {
            Write-Warn "Nao existe: $appName"
        }
    }
    Write-Host "`n=== Container Apps removidos (infra preservada) ===" -ForegroundColor Green
}

# =====================================================================
# Execução principal
# =====================================================================
Test-Prerequisites

switch ($Mode) {
    "infra" { Start-Infrastructure }
    "apps"  { Deploy-Apps }
    "all"   { Start-Infrastructure; Deploy-Apps }
    "down"  { Stop-Apps }
}
