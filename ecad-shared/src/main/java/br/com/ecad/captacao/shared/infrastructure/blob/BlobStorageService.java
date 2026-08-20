package br.com.ecad.captacao.shared.infrastructure.blob;

import br.com.ecad.captacao.shared.domain.exceptions.BlobStorageException;

/**
 * Interface unificada de serviço de Blob Storage.
 * Combina as operações necessárias por processing-engine e document-scraper.
 */
public interface BlobStorageService {

    /**
     * Faz upload de conteúdo para a área de staging.
     *
     * @param content      bytes do arquivo
     * @param stagingPath  caminho de staging (ex: "staging/")
     * @param fileName     nome do arquivo
     * @return URL do blob armazenado
     */
    String uploadStaging(byte[] content, String stagingPath, String fileName) throws BlobStorageException;

    /**
     * Faz download de um blob.
     *
     * @param blobUrl URL do blob
     * @return conteúdo + contentType
     */
    BlobDownload download(String blobUrl) throws BlobStorageException;

    /**
     * Move blob de staging para produção.
     *
     * @param stagingUrl URL do blob em staging
     * @return URL do blob em produção
     */
    String moveToProduction(String stagingUrl) throws BlobStorageException;

    /**
     * Remove um blob (best-effort, tolerante a blob inexistente).
     *
     * @param blobUrl URL do blob
     */
    void delete(String blobUrl) throws BlobStorageException;
    }