package br.com.ecad.captacao.shared.infrastructure.blob;

import br.com.ecad.captacao.shared.domain.exceptions.BlobStorageException;
import br.com.ecad.captacao.shared.infrastructure.local.LocalBlobStorage;
import br.com.ecad.captacao.shared.infrastructure.local.LocalDevelopmentSettings;
import org.springframework.stereotype.Service;

/**
 * Implementação padrão de {@link BlobStorageService} que faz fallback entre
 * storage cloud (Azure Blob) e storage local (filesystem).
 *
 * <p>Se {@link #cloudStorage} estiver configurado (não null), usa ele.
 * Caso contrário, usa {@link #localStorage}.</p>
 */
@Service
public class DefaultBlobStorageService implements BlobStorageService {

    private final BlobStorage cloudStorage;
    private final LocalBlobStorage localStorage;
    private final String stagingPrefix;
    private final String producaoPrefix;

    public DefaultBlobStorageService(
        BlobStorage cloudStorage,
        LocalBlobStorage localStorage,
        LocalDevelopmentSettings localDevelopment,
        String stagingPrefix,
        String producaoPrefix) {
        this.cloudStorage = cloudStorage;
        this.localStorage = localStorage;
        this.stagingPrefix = stagingPrefix;
        this.producaoPrefix = producaoPrefix;
    }

    @Override
    public String uploadStaging(byte[] content, String stagingPath, String fileName) throws BlobStorageException {
        try {
            String relativePath = (stagingPath + "/" + fileName).replace('\\', '/').replaceAll("^/+", "");
            BlobStorage storage = getStorage();
            return storage.upload(content, relativePath, contentType(fileName));
        } catch (Exception ex) {
            throw new BlobStorageException("Falha ao fazer upload para staging: " + stagingPath + "/" + fileName, ex);
        }
    }

    @Override
    public BlobDownload download(String blobUrl) throws BlobStorageException {
        try {
            BlobStorage storage = getStorage();
            var download = storage.download(blobUrl);
            return new BlobDownload(download.content(), download.contentType());
        } catch (BlobStorageException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BlobStorageException("Falha ao baixar blob: " + blobUrl, ex);
        }
    }

    @Override
    public String moveToProduction(String stagingUrl) throws BlobStorageException {
        try {
            BlobStorage storage = getStorage();
            return storage.move(stagingUrl, stagingPrefix, producaoPrefix);
        } catch (BlobStorageException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BlobStorageException("Falha ao mover blob para produção: " + stagingUrl, ex);
        }
    }

    @Override
    public void delete(String blobUrl) throws BlobStorageException {
        if (blobUrl == null || blobUrl.isBlank()) {
            return;
        }
        try {
            BlobStorage storage = getStorage();
            storage.delete(blobUrl);
        } catch (BlobStorageException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BlobStorageException("Falha ao deletar blob: " + blobUrl, ex);
        }
    }

    private BlobStorage getStorage() {
        return cloudStorage != null ? cloudStorage : localStorage;
    }

    private static String contentType(String fileName) {
        String lower = fileName.toLowerCase(java.util.Locale.ROOT);
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".json")) return "application/json";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".md") || lower.endsWith(".txt")) return "text/plain";
        return "application/octet-stream";
    }
}