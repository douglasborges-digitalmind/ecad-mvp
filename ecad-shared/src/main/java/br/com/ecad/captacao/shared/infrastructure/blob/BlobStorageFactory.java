package br.com.ecad.captacao.shared.infrastructure.blob;

import br.com.ecad.captacao.shared.infrastructure.azure.AzureBlobStorage;
import br.com.ecad.captacao.shared.infrastructure.local.LocalBlobStorage;
import br.com.ecad.captacao.shared.infrastructure.local.LocalDevelopmentSettings;

/**
 * Factory para criar instâncias de {@link BlobStorage}.
 * Centraliza a lógica de seleção entre storage cloud (Azure) e local.
 */
public final class BlobStorageFactory {

    private BlobStorageFactory() {
    }

    /**
     * Cria um BlobStorage baseado na configuração.
     *
     * @param connectionString connection string do Azure Blob (pode ser vazio)
     * @param containerName nome do container
     * @param localDevelopment configuração de desenvolvimento local
     * @return BlobStorage configurado ou null se não houver connection string
     */
    public static BlobStorage create(
        String connectionString,
        String containerName,
        LocalDevelopmentSettings localDevelopment) {

        if (localDevelopment.enabled) {
            return new LocalBlobStorage(localDevelopment);
        }

        if (connectionString == null || connectionString.isBlank()) {
            return null;
        }

        return new AzureBlobStorage(connectionString, containerName);
    }
}