package br.com.ecad.captacao.shared.domain.exceptions;

/**
 * Falha em operações de Blob Storage (upload, download, delete, move).
 */
public class BlobStorageException extends EcadDomainException {

    private static final long serialVersionUID = 1L;

    public BlobStorageException(String message) {
        super(message);
    }

    public BlobStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}