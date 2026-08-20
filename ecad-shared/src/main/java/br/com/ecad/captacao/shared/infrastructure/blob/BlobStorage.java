package br.com.ecad.captacao.shared.infrastructure.blob;

import java.io.IOException;

/**
 * Interface neutra de object storage (Blob / Local).
 *
 * <p>Substitui os wrappers Azure e Local com um contrato único,
 * permitindo que a solução rode em qualquer nuvem ou localmente.</p>
 */
public interface BlobStorage {

    /**
     * Faz upload de conteúdo para o storage.
     *
     * @param content      bytes do arquivo
     * @param relativePath caminho relativo dentro do container/bucket
     * @param contentType  MIME type (pode ser null/blank para auto-detecção)
     * @return URL ou identificador do objeto armazenado
     */
    String upload(byte[] content, String relativePath, String contentType) throws IOException;

    /**
     * Faz download de conteúdo do storage.
     *
     * @param blobUrl URL ou nome do blob
     * @return conteúdo + contentType
     */
    BlobDownload download(String blobUrl) throws IOException;

    /**
     * Move/renomeia um blob entre prefixos (staging → produção).
     *
     * @param blobUrl    URL ou nome do blob de origem
     * @param fromPrefix prefixo de origem (ex: "staging-area/")
     * @param toPrefix   prefixo de destino (ex: "producao/")
     * @return URL do blob no destino
     */
    String move(String blobUrl, String fromPrefix, String toPrefix) throws IOException;

    /**
     * Remove um blob.
     *
     * @param blobUrl URL ou nome do blob
     */
    void delete(String blobUrl) throws IOException;

    /**
     * Verifica se um blob existe.
     *
     * @param blobUrl URL ou nome do blob
     * @return true se existir
     */
    boolean exists(String blobUrl) throws IOException;
}