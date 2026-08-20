package br.com.ecad.captacao.documentscraper;

import java.util.Arrays;
import java.util.Locale;

import br.com.ecad.captacao.shared.infrastructure.config.AiProviderSettings;
import br.com.ecad.captacao.shared.infrastructure.config.EnvReaders;
import br.com.ecad.captacao.shared.infrastructure.local.LocalDevelopmentSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;

record DocumentScraperSettings(
    String kafkaBootstrapServers,
    String kafkaSecurityProtocol,
    String kafkaSaslMechanism,
    String kafkaSaslJaasConfig,
    String kafkaSaslUsername,
    String kafkaSaslPassword,
    String scrapingCommandsTopic,
    String capturedDocumentsTopic,
    String consumerGroup,
    String azureStorageConnectionString,
    String azureBlobContainerName,
    int maxEventProcessingAttempts,
    String mongoConnectionString,
    String mongoDatabaseName,
    String geminiApiKey,
    String geminiModel,
    String outputFolder,
    String localConsumerRoute,
    AiProviderSettings aiProvider,
    int maxPaginationPages,
    String optionalPipelinesRootPath,
    String[] enabledOptionalPipelines,
    int kafkaMaxPollIntervalMs,
    int kafkaMaxPollRecords
) {
    private static final Logger LOGGER = LoggerFactory.getLogger(DocumentScraperSettings.class);

    static DocumentScraperSettings fromEnvironment(Environment environment) {
        return new DocumentScraperSettings(
            EnvReaders.read(environment, "KAFKA_BOOTSTRAP_SERVERS", ""),
            EnvReaders.read(environment, "KAFKA_SECURITY_PROTOCOL", ""),
            EnvReaders.read(environment, "KAFKA_SASL_MECHANISM", ""),
            EnvReaders.read(environment, "KAFKA_SASL_JAAS_CONFIG", ""),
            EnvReaders.read(environment, "KAFKA_SASL_USERNAME", ""),
            EnvReaders.read(environment, "KAFKA_SASL_PASSWORD", ""),
            EnvReaders.read(environment, "KAFKA_SCRAPING_COMMANDS_TOPIC", "scraping_commands"),
            EnvReaders.read(environment, "KAFKA_CAPTURED_DOCUMENTS_TOPIC", "captured_documents"),
            EnvReaders.read(environment, "KAFKA_CONSUMER_GROUP", "cg-document-scraper"),
            EnvReaders.read(environment, "AZURE_STORAGE_CONNECTION_STRING", ""),
            EnvReaders.read(environment, "AZURE_BLOB_CONTAINER_NAME", "captura-documentos"),
            EnvReaders.readInt(environment, "MAX_EVENT_PROCESSING_ATTEMPTS", 3),
            EnvReaders.read(environment, "MONGODB_CONNECTION_STRING", ""),
            EnvReaders.read(environment, "MONGODB_DATABASE_NAME", "ecad-captacao"),
            EnvReaders.read(environment, "GEMINI_API_KEY", ""),
            EnvReaders.read(environment, "GEMINI_MODEL", "gemini-2.5-flash"),
            EnvReaders.read(environment, "SCRAPER_OUTPUT_FOLDER", ""),
            EnvReaders.read(environment, "DOCUMENT_SCRAPER_LOCAL_ROUTE", "document-scraper"),
            AiProviderSettings.fromEnvironment(environment, "document-scraper"),
            EnvReaders.readInt(environment, "MAX_PAGINATION_PAGES", 12),
            EnvReaders.read(environment, "OPTIONAL_PIPELINES_ROOT_PATH", ""),
            EnvReaders.split(EnvReaders.read(environment, "ENABLED_OPTIONAL_PIPELINES", "")),
            EnvReaders.readInt(environment, "KAFKA_MAX_POLL_INTERVAL_MS", 3_600_000),
            EnvReaders.readInt(environment, "KAFKA_MAX_POLL_RECORDS", 1));
    }

    void validate(LocalDevelopmentSettings localDevelopment) {
        if (localDevelopment.enabled) {
            return;
        }
        var missing = new java.util.ArrayList<String>();
        if (kafkaBootstrapServers.isBlank()) {
            missing.add("KAFKA_BOOTSTRAP_SERVERS");
        }
        if (azureStorageConnectionString.isBlank()) {
            missing.add("AZURE_STORAGE_CONNECTION_STRING");
        }
        if (mongoConnectionString.isBlank()) {
            missing.add("MONGODB_CONNECTION_STRING");
        }

        for (var provider : aiProvider.getAiProviders()) {
            switch (provider.toLowerCase(Locale.ROOT)) {
                case "openrouter" -> {
                    if (aiProvider.openRouterApiKey().isBlank()) {
                        LOGGER.warn("Provider de IA 'openrouter' sem OPENROUTER_API_KEY configurada; sera ignorado e a cadeia seguira para o proximo provider (fallback).");
                    }
                }
                case "gemini", "gemini_nativo" -> {
                    if (aiProvider.geminiApiKey().isBlank()) {
                        LOGGER.warn("Provider de IA '{}' sem GEMINI_API_KEY configurada; sera ignorado e a cadeia seguira para o proximo provider (fallback).", provider);
                    }
                }
                case "ollama" -> {
                    if (aiProvider.ollamaBaseUrl().isBlank()) {
                        missing.add("OLLAMA_BASE_URL");
                    }
                    if (aiProvider.ollamaModel().isBlank()) {
                        missing.add("OLLAMA_MODEL");
                    }
                }
                case "azure_openai" -> {
                    if (aiProvider.azureOpenAiEndpoint().isBlank() || aiProvider.azureOpenAiApiKey().isBlank() || aiProvider.azureOpenAiDeployment().isBlank()) {
                        LOGGER.warn("Provider de IA 'azure_openai' sem credenciais completas (AZURE_OPENAI_ENDPOINT, AZURE_OPENAI_API_KEY, AZURE_OPENAI_DEPLOYMENT); sera ignorado e a cadeia seguira para o proximo provider (fallback).");
                    }
                }
                default -> {
                }
            }
        }

        if (!missing.isEmpty()) {
            throw new IllegalStateException("Configuracoes obrigatorias ausentes: " + String.join(", ", missing.stream().distinct().toList()));
        }
    }

    String[] getAiProviders() {
        return aiProvider.getAiProviders();
    }

    boolean isProviderConfigured(String provider) {
        return aiProvider.isProviderConfigured(provider);
    }
}
