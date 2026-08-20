package br.com.ecad.captacao.processingengine;

import java.util.ArrayList;
import java.util.Arrays;

import br.com.ecad.captacao.shared.infrastructure.config.AiProviderSettings;
import br.com.ecad.captacao.shared.infrastructure.config.EnvReaders;
import br.com.ecad.captacao.shared.infrastructure.local.LocalDevelopmentSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.core.env.Environment;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "ecad.processing-engine")
@Validated
public record ProcessingEngineSettings(
    @DefaultValue("") String kafkaBootstrapServers,
    @DefaultValue("") String kafkaSecurityProtocol,
    @DefaultValue("") String kafkaSaslMechanism,
    @DefaultValue("") String kafkaSaslJaasConfig,
    @DefaultValue("") String kafkaSaslUsername,
    @DefaultValue("") String kafkaSaslPassword,
    @DefaultValue("captured_documents") String capturedDocumentsTopic,
    @DefaultValue("processing-engine") String localConsumerRoute,
    @DefaultValue("processing-engine") String instanceId,
    @DefaultValue("5") int localHeartbeatIntervalSeconds,
    @DefaultValue("cg-processing-engine") String kafkaConsumerGroup,
    @DefaultValue("") String mongoConnectionString,
    @DefaultValue("ecad-captacao") String mongoDatabaseName,
    @DefaultValue("") String azureStorageConnectionString,
    @DefaultValue("") String azureBlobContainerName,
    @DefaultValue("staging/") String blobStagingPrefix,
    @DefaultValue("producao/") String blobProducaoPrefix,
    @DefaultValue("3") int maxEventProcessingAttempts,
    AiProviderSettings aiProvider,
    @DefaultValue("true") boolean sgaVerificationEnabled,
    @DefaultValue("https://api-prd.ecad.org.br/oauth-rfc/v1/access-token") String sgaOAuthUrl,
    @DefaultValue("https://backend.ecad.org.br/arrecadacao/api-show") String sgaBaseUrl,
    @DefaultValue("") String sgaAuthorization,
    @DefaultValue("") String sgaClientId,
    @DefaultValue("") String sgaClientSecret,
    @DefaultValue("") String sgaUser,
    @DefaultValue("10") int sgaTimeoutSeconds,
    @DefaultValue("3") int sgaMaxRetries,
    @DefaultValue("3600000") int kafkaMaxPollIntervalMs,
    @DefaultValue("1") int kafkaMaxPollRecords
) {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessingEngineSettings.class);

    public String[] getAiProviders() {
        return aiProvider.getAiProviders();
    }

    /**
     * Failfast no boot quando rodando em modo cloud (LOCAL_DEVELOPMENT_ENABLED=false): se qualquer
     * connection-string obrigatoria (EventHubs, Cosmos, Blob) estiver em branco, lanca IllegalStateException
     * listando todas as variaveis ausentes. Em modo local, valida apenas que pelo menos um provider de IA
     * declarado em AI_PROVIDER_CHAIN tem credenciais configuradas.
     */
    public void validate(LocalDevelopmentSettings localDevelopment) {
        var missing = new ArrayList<String>();
        if (!localDevelopment.enabled) {
            var hasKafka = !kafkaBootstrapServers.isBlank();
            var hasMongo = !mongoConnectionString.isBlank();
            var hasBlob = !azureStorageConnectionString.isBlank();
            if (!hasKafka) missing.add("KAFKA_BOOTSTRAP_SERVERS");
            if (!hasMongo) missing.add("MONGODB_CONNECTION_STRING");
            if (!hasBlob) missing.add("AZURE_STORAGE_CONNECTION_STRING");
            if (sgaVerificationEnabled) {
                if (sgaClientId.isBlank()) missing.add("SGA_CLIENT_ID");
                if (sgaClientSecret.isBlank()) missing.add("SGA_CLIENT_SECRET");
            }
        }
        var providers = getAiProviders();
        for (var provider : providers) {
            if (!aiProvider.isProviderConfigured(provider)) {
                LOGGER.warn("Provider de IA '{}' sem credenciais configuradas; sera ignorado e a cadeia seguira para o proximo provider (fallback).", provider);
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Configuracoes obrigatorias ausentes: " + String.join(", ", missing));
        }
    }

    public String[] getUnsupportedAiProviders() {
        return aiProvider.getUnsupportedAiProviders();
    }

    public String[] getConfiguredAiProviders() {
        return aiProvider.getConfiguredAiProviders();
    }

    public String[] getUnavailableAiProviders() {
        return Arrays.stream(getAiProviders()).filter(provider -> !aiProvider.isProviderConfigured(provider)).toArray(String[]::new);
    }

    static ProcessingEngineSettings fromEnvironment(Environment environment) {
        return new ProcessingEngineSettings(
            EnvReaders.read(environment, "ecad.processing-engine.kafka-bootstrap-servers", "KAFKA_BOOTSTRAP_SERVERS", ""),
            EnvReaders.read(environment, "ecad.processing-engine.kafka-security-protocol", "KAFKA_SECURITY_PROTOCOL", ""),
            EnvReaders.read(environment, "ecad.processing-engine.kafka-sasl-mechanism", "KAFKA_SASL_MECHANISM", ""),
            EnvReaders.read(environment, "ecad.processing-engine.kafka-sasl-jaas-config", "KAFKA_SASL_JAAS_CONFIG", ""),
            EnvReaders.read(environment, "ecad.processing-engine.kafka-sasl-username", "KAFKA_SASL_USERNAME", ""),
            EnvReaders.read(environment, "ecad.processing-engine.kafka-sasl-password", "KAFKA_SASL_PASSWORD", ""),
            EnvReaders.read(environment, "ecad.processing-engine.captured-documents-topic", "KAFKA_CAPTURED_DOCUMENTS_TOPIC", "captured_documents"),
            EnvReaders.read(environment, "ecad.processing-engine.local-consumer-route", "LOCAL_CONSUMER_ROUTE", "processing-engine"),
            EnvReaders.read(environment, "ecad.processing-engine.instance-id", "INSTANCE_ID", "processing-engine"),
            EnvReaders.readInt(environment, "ecad.processing-engine.local-heartbeat-interval-seconds", "LOCAL_HEARTBEAT_INTERVAL_SECONDS", 5),
            EnvReaders.read(environment, "ecad.processing-engine.kafka-consumer-group", "KAFKA_CONSUMER_GROUP", "cg-processing-engine"),
            EnvReaders.read(environment, "ecad.processing-engine.mongo-connection-string", "MONGODB_CONNECTION_STRING", ""),
            EnvReaders.read(environment, "ecad.processing-engine.mongo-database-name", "MONGODB_DATABASE_NAME", "ecad-captacao"),
            EnvReaders.read(environment, "ecad.processing-engine.azure-storage-connection-string", "AZURE_STORAGE_CONNECTION_STRING", ""),
            EnvReaders.read(environment, "ecad.processing-engine.azure-blob-container-name", "AZURE_BLOB_CONTAINER_NAME", "captura-documentos"),
            EnvReaders.read(environment, "ecad.processing-engine.blob-staging-prefix", "BLOB_STAGING_PREFIX", "staging/"),
            EnvReaders.read(environment, "ecad.processing-engine.blob-producao-prefix", "BLOB_PRODUCAO_PREFIX", "producao/"),
            EnvReaders.readInt(environment, "ecad.processing-engine.max-event-processing-attempts", "MAX_EVENT_PROCESSING_ATTEMPTS", 3),
            AiProviderSettings.fromEnvironment(environment, "ecad.processing-engine"),
            EnvReaders.readBoolean(environment, "ecad.processing-engine.sga-verification-enabled", "SGA_VERIFICATION_ENABLED", true),
            EnvReaders.read(environment, "ecad.processing-engine.sga-oauth-url", "SGA_OAUTH_URL", "https://api-prd.ecad.org.br/oauth-rfc/v1/access-token"),
            EnvReaders.read(environment, "ecad.processing-engine.sga-base-url", "SGA_BASE_URL", "https://backend.ecad.org.br/arrecadacao/api-show"),
            EnvReaders.read(environment, "ecad.processing-engine.sga-authorization", "SGA_AUTHORIZATION", ""),
            EnvReaders.read(environment, "ecad.processing-engine.sga-client-id", "SGA_CLIENT_ID", ""),
            EnvReaders.read(environment, "ecad.processing-engine.sga-client-secret", "SGA_CLIENT_SECRET", ""),
            EnvReaders.read(environment, "ecad.processing-engine.sga-user", "SGA_USER", ""),
            EnvReaders.readInt(environment, "ecad.processing-engine.sga-timeout-seconds", "SGA_TIMEOUT_SECONDS", 10),
            EnvReaders.readInt(environment, "ecad.processing-engine.sga-max-retries", "SGA_MAX_RETRIES", 3),
            EnvReaders.readInt(environment, "ecad.processing-engine.kafka-max-poll-interval-ms", "KAFKA_MAX_POLL_INTERVAL_MS", 3_600_000),
            EnvReaders.readInt(environment, "ecad.processing-engine.kafka-max-poll-records", "KAFKA_MAX_POLL_RECORDS", 1)
        );
    }
}
