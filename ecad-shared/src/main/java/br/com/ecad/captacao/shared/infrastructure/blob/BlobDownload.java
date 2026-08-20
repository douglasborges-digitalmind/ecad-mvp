package br.com.ecad.captacao.shared.infrastructure.blob;

/**
 * Resultado de um download do Blob Storage.
 *
 * @param content     bytes do arquivo
 * @param contentType MIME type do conteúdo
 */
public record BlobDownload(byte[] content, String contentType) {
}