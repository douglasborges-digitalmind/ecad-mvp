package br.com.ecad.captacao.processingengine;

import br.com.ecad.captacao.shared.infrastructure.blob.BlobStorage;
import br.com.ecad.captacao.shared.infrastructure.blob.BlobStorageFactory;
import br.com.ecad.captacao.shared.infrastructure.local.LocalDevelopmentSettings;
import br.com.ecad.captacao.shared.infrastructure.mongodb.MongoFactory;

class ProcessingCloudClients implements AutoCloseable {
    private final com.mongodb.client.MongoClient mongoClient;
    private final BlobStorage blobStorage;

    ProcessingCloudClients(ProcessingEngineSettings settings, LocalDevelopmentSettings localDevelopment) {
        if (localDevelopment.enabled) {
            mongoClient = null;
            blobStorage = null;
            return;
        }

        mongoClient = MongoFactory.create(settings.mongoConnectionString());

        blobStorage = BlobStorageFactory.create(
            settings.azureStorageConnectionString(),
            settings.azureBlobContainerName(),
            localDevelopment);
    }

    com.mongodb.client.MongoClient mongoClient() { return mongoClient; }
    BlobStorage blobStorage() { return blobStorage; }
    boolean hasMongoClient() { return mongoClient != null; }
    boolean hasBlobStorage() { return blobStorage != null; }

    @Override
    public void close() {
        if (mongoClient != null) mongoClient.close();
    }
}
