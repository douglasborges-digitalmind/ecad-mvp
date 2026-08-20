package br.com.ecad.captacao.documentscraper;

import br.com.ecad.captacao.shared.infrastructure.blob.BlobStorage;
import br.com.ecad.captacao.shared.infrastructure.blob.BlobStorageFactory;
import br.com.ecad.captacao.shared.infrastructure.local.LocalDevelopmentSettings;
import br.com.ecad.captacao.shared.infrastructure.messaging.MessagePublisher;
import br.com.ecad.captacao.shared.infrastructure.kafka.KafkaMessagePublisher;
import br.com.ecad.captacao.shared.infrastructure.mongodb.MongoFactory;

class DocumentScraperCloudClients implements AutoCloseable {
    private final com.mongodb.client.MongoClient mongoClient;
    private final BlobStorage blobStorage;
    private final MessagePublisher capturedDocumentPublisher;

    DocumentScraperCloudClients(DocumentScraperSettings settings, LocalDevelopmentSettings localDevelopment) {
        if (localDevelopment.enabled) {
            mongoClient = null;
            blobStorage = null;
            capturedDocumentPublisher = null;
            return;
        }

        mongoClient = MongoFactory.create(settings.mongoConnectionString());

        blobStorage = BlobStorageFactory.create(
            settings.azureStorageConnectionString(),
            settings.azureBlobContainerName(),
            localDevelopment);

        capturedDocumentPublisher = !settings.kafkaBootstrapServers().isBlank()
            ? new KafkaMessagePublisher(settings.kafkaBootstrapServers(), settings.kafkaSecurityProtocol(), settings.kafkaSaslMechanism(), settings.kafkaSaslJaasConfig(), settings.kafkaSaslUsername(), settings.kafkaSaslPassword())
            : null;
    }

    com.mongodb.client.MongoClient mongoClient() { return mongoClient; }
    BlobStorage blobStorage() { return blobStorage; }
    MessagePublisher capturedDocumentPublisher() { return capturedDocumentPublisher; }
    boolean hasMongoClient() { return mongoClient != null; }
    boolean hasBlobStorage() { return blobStorage != null; }
    boolean hasCapturedDocumentPublisher() { return capturedDocumentPublisher != null; }

    @Override
    public void close() {
        if (capturedDocumentPublisher instanceof AutoCloseable c) { try { c.close(); } catch (Exception ignored) {} }
        if (mongoClient != null) mongoClient.close();
    }
}
