package br.com.ecad.captacao.controlcenter;

import br.com.ecad.captacao.shared.infrastructure.local.LocalDevelopmentSettings;
import org.springframework.core.env.Environment;

record ControlCenterSettings(
    String kafkaBootstrapServers,
    String kafkaSecurityProtocol,
    String kafkaSaslMechanism,
    String kafkaSaslJaasConfig,
    String kafkaSaslUsername,
    String kafkaSaslPassword,
    String scrapingCommandsTopic,
    String capturedDocumentsTopic,
    String mongoConnectionString,
    String mongoDatabaseName
) {
    static ControlCenterSettings fromEnvironment(Environment environment) {
        return new ControlCenterSettings(
            environment.getProperty("KAFKA_BOOTSTRAP_SERVERS", ""),
            environment.getProperty("KAFKA_SECURITY_PROTOCOL", ""),
            environment.getProperty("KAFKA_SASL_MECHANISM", ""),
            environment.getProperty("KAFKA_SASL_JAAS_CONFIG", ""),
            environment.getProperty("KAFKA_SASL_USERNAME", ""),
            environment.getProperty("KAFKA_SASL_PASSWORD", ""),
            environment.getProperty("KAFKA_SCRAPING_COMMANDS_TOPIC", "scraping_commands"),
            environment.getProperty("KAFKA_CAPTURED_DOCUMENTS_TOPIC", "captured_documents"),
            environment.getProperty("MONGODB_CONNECTION_STRING", ""),
            environment.getProperty("MONGODB_DATABASE_NAME", "ecad-captacao"));
    }

    void validate(LocalDevelopmentSettings localDevelopment) {
        if (localDevelopment.enabled) return;
        var missing = new java.util.ArrayList<String>();
        if (kafkaBootstrapServers.isBlank()) missing.add("KAFKA_BOOTSTRAP_SERVERS");
        if (mongoConnectionString.isBlank()) missing.add("MONGODB_CONNECTION_STRING");
        if (!missing.isEmpty())
            throw new IllegalStateException("Configuracoes obrigatorias ausentes: " + String.join(", ", missing));
    }
}