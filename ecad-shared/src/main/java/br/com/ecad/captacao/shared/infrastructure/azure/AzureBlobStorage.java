package br.com.ecad.captacao.shared.infrastructure.azure;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import br.com.ecad.captacao.shared.infrastructure.blob.BlobDownload;
import br.com.ecad.captacao.shared.infrastructure.blob.BlobStorage;
import com.azure.core.util.BinaryData;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.BlobHttpHeaders;
import com.azure.storage.blob.models.BlobProperties;
import com.azure.storage.blob.models.BlobStorageException;
import com.azure.storage.blob.specialized.BlockBlobClient;

/**
 * Implementação Azure Blob Storage de {@link BlobStorage}.
 * Usa {@link BlobServiceClientBuilder} + {@link BlobContainerClient}.
 */
public final class AzureBlobStorage implements BlobStorage {
    private final BlobContainerClient containerClient;
    private final String containerUrlPrefix;

    public AzureBlobStorage(String connectionString, String containerName) {
        BlobServiceClient serviceClient = new BlobServiceClientBuilder()
            .connectionString(connectionString)
            .buildClient();

        this.containerClient = serviceClient.getBlobContainerClient(containerName);
            // Ensure container exists (idempotent)
            this.containerClient.createIfNotExists();
            this.containerUrlPrefix = containerClient.getBlobContainerUrl() + "/";
        }

    @Override
    public String upload(byte[] content, String relativePath, String contentType) throws IOException {
        String key = normalizeKey(relativePath);
        BlockBlobClient blobClient = containerClient.getBlobClient(key).getBlockBlobClient();
        
        blobClient.upload(BinaryData.fromBytes(content), true);
        
        // Set content type if provided
        if (contentType != null && !contentType.isBlank()) {
            blobClient.setHttpHeaders(new BlobHttpHeaders()
                .setContentType(contentType));
        }
        
        return containerUrlPrefix + key;
    }

    @Override
    public BlobDownload download(String blobUrl) throws IOException {
        String key = resolveKey(blobUrl);
        BlobClient blobClient = containerClient.getBlobClient(key);
        
        try (InputStream stream = blobClient.openInputStream()) {
            byte[] bytes = stream.readAllBytes();
            String contentType = Optional.ofNullable(blobClient.getProperties())
                .map(BlobProperties::getContentType)
                .orElse("application/octet-stream");
            return new BlobDownload(bytes, contentType);
        } catch (com.azure.storage.blob.models.BlobStorageException e) {
            if (e.getStatusCode() == 404) {
                throw new IOException("Blob não encontrado: " + blobUrl, e);
            }
            throw new IOException("Erro ao baixar blob: " + blobUrl, e);
        }
    }

    @Override
    public String move(String blobUrl, String fromPrefix, String toPrefix) throws IOException {
        String sourceKey = resolveKey(blobUrl);
        String normalizedFromPrefix = trimSlashes(fromPrefix);
        String normalizedToPrefix = trimSlashes(toPrefix);

        String destinationKey;
        if (sourceKey.equalsIgnoreCase(normalizedFromPrefix) 
            || sourceKey.toLowerCase().startsWith(normalizedFromPrefix.toLowerCase() + "/")) {
            destinationKey = normalizedToPrefix + sourceKey.substring(normalizedFromPrefix.length());
        } else {
            destinationKey = normalizedToPrefix + "/" + sourceKey.substring(sourceKey.lastIndexOf('/') + 1);
        }

        // Copy blob within the same storage account (null source conditions, null duration = no timeout)
        BlobClient sourceBlob = containerClient.getBlobClient(sourceKey);
        BlockBlobClient destBlob = containerClient.getBlobClient(destinationKey).getBlockBlobClient();
        
        destBlob.beginCopy(sourceBlob.getBlobUrl(), null).waitForCompletion();

        // Verify copy succeeded: waitForCompletion throws on failure; check copy status for extra safety
        BlobProperties props = destBlob.getProperties();
        var copyStatus = props.getCopyStatus();
        if (copyStatus != null && !"success".equalsIgnoreCase(copyStatus.toString())) {
            throw new IOException("Falha ao copiar blob: " + copyStatus);
        }

        // Delete source - ignore if not found (already moved)
        try {
            sourceBlob.delete();
        } catch (BlobStorageException e) {
            if (e.getStatusCode() != 404) {
                throw new IOException("Falha ao deletar blob origem: " + sourceKey, e);
            }
        }
        
        return containerUrlPrefix + destinationKey;
    }

    @Override
    public void delete(String blobUrl) throws IOException {
        String key = resolveKey(blobUrl);
        containerClient.getBlobClient(key).delete();
    }

    @Override
    public boolean exists(String blobUrl) throws IOException {
        try {
            String key = resolveKey(blobUrl);
            return containerClient.getBlobClient(key).exists();
        } catch (Exception e) {
            return false;
        }
    }

    private String resolveKey(String blobUrl) {
        String decoded = URLDecoder.decode(blobUrl, StandardCharsets.UTF_8);
        if (decoded.startsWith(containerUrlPrefix)) {
            return decoded.substring(containerUrlPrefix.length());
        }
        // Assume it's already a key
        return decoded;
    }

    private String normalizeKey(String relativePath) {
        return relativePath.replace('\\', '/').replaceAll("^/+", "");
    }

    private String trimSlashes(String s) {
        return s.replace('\\', '/').replaceAll("^/+|/+$", "");
    }
}