package br.com.ecad.captacao.controlcenter;

import br.com.ecad.captacao.shared.infrastructure.local.LocalDevelopmentSettings;
import br.com.ecad.captacao.shared.infrastructure.messaging.MessagePublisher;
import br.com.ecad.captacao.shared.infrastructure.kafka.KafkaMessagePublisher;

class ControlCenterCloudClients implements AutoCloseable {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(ControlCenterCloudClients.class);
    private final com.mongodb.client.MongoClient mongoClient;
    private final MessagePublisher scrapingCommandPublisher;
    private final MessagePublisher capturedDocumentPublisher;

    ControlCenterCloudClients(ControlCenterSettings settings, LocalDevelopmentSettings localDevelopment) {
        if (localDevelopment.enabled) {
            mongoClient = null;
            scrapingCommandPublisher = null;
            capturedDocumentPublisher = null;
            LOGGER.warn("Modo desenvolvimento local ativo — MongoDB e Kafka desabilitados");
            return;
        }

        mongoClient = !settings.mongoConnectionString().isBlank()
            ? br.com.ecad.captacao.shared.infrastructure.mongodb.MongoClientFactory.create(settings.mongoConnectionString())
            : null;
        LOGGER.info("MongoDB: {}", mongoClient != null ? "conectado" : "DESABILITADO (connection string vazia)");

        var kafkaPublisher = !settings.kafkaBootstrapServers().isBlank()
            ? new KafkaMessagePublisher(settings.kafkaBootstrapServers(), settings.kafkaSecurityProtocol(), settings.kafkaSaslMechanism(), settings.kafkaSaslJaasConfig(), settings.kafkaSaslUsername(), settings.kafkaSaslPassword())
            : null;
        scrapingCommandPublisher = kafkaPublisher;
        capturedDocumentPublisher = kafkaPublisher;
        LOGGER.info("Kafka: {}", kafkaPublisher != null ? "conectado" : "DESABILITADO (bootstrap servers vazio)");
    }

    com.mongodb.client.MongoClient mongoClient() { return mongoClient; }
    MessagePublisher scrapingCommandPublisher() { return scrapingCommandPublisher; }
    MessagePublisher capturedDocumentPublisher() { return capturedDocumentPublisher; }
    boolean hasMongoClient() { return mongoClient != null; }
    boolean hasKafkaPublisher() { return scrapingCommandPublisher != null; }

    @Override
    public void close() {
        if (scrapingCommandPublisher instanceof AutoCloseable c) { try { c.close(); } catch (Exception ignored) {} }
        if (mongoClient != null) mongoClient.close();
    }
}