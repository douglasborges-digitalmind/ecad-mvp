package br.com.ecad.captacao.shared;

import java.util.List;

public final class EcadEnvironment {
    public static final String LOCAL_DEVELOPMENT_ENABLED = "LOCAL_DEVELOPMENT_ENABLED";
    public static final String LOCAL_DEVELOPMENT_ROOT = "LOCAL_DEVELOPMENT_ROOT";

    // Cloud-agnostic keys (MongoDB, Kafka, Azure Blob)
    public static final String MONGODB_CONNECTION_STRING = "MONGODB_CONNECTION_STRING";
    public static final String MONGODB_DATABASE_NAME = "MONGODB_DATABASE_NAME";
    public static final String KAFKA_BOOTSTRAP_SERVERS = "KAFKA_BOOTSTRAP_SERVERS";
    public static final String KAFKA_SCRAPING_COMMANDS_TOPIC = "KAFKA_SCRAPING_COMMANDS_TOPIC";
    public static final String KAFKA_CAPTURED_DOCUMENTS_TOPIC = "KAFKA_CAPTURED_DOCUMENTS_TOPIC";
    public static final String AZURE_STORAGE_CONNECTION_STRING = "AZURE_STORAGE_CONNECTION_STRING";
    public static final String AZURE_BLOB_CONTAINER_NAME = "AZURE_BLOB_CONTAINER_NAME";

    public static final String AI_PROVIDER_CHAIN = "AI_PROVIDER_CHAIN";
    public static final String GEMINI_API_KEY = "GEMINI_API_KEY";
    public static final String OPENROUTER_API_KEY = "OPENROUTER_API_KEY";
    public static final String OLLAMA_BASE_URL = "OLLAMA_BASE_URL";
    public static final String EMAIL_CONNECTION_STRING = "EMAIL_CONNECTION_STRING";

    public static final List<String> CLOUD_SETTINGS = List.of(
        MONGODB_CONNECTION_STRING,
        MONGODB_DATABASE_NAME,
        KAFKA_BOOTSTRAP_SERVERS,
        AZURE_STORAGE_CONNECTION_STRING
    );

    private EcadEnvironment() {
    }
}
