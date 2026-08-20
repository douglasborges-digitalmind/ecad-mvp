package br.com.ecad.captacao.documentscraper;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import br.com.ecad.captacao.shared.contracts.DocumentoCapturado;
import br.com.ecad.captacao.shared.contracts.ExecutarScraping;
import br.com.ecad.captacao.shared.domain.entities.Documento;
import br.com.ecad.captacao.shared.domain.enums.TipoEvidencia;
import br.com.ecad.captacao.shared.infrastructure.blob.BlobStorageService;
import br.com.ecad.captacao.shared.infrastructure.repositories.DocumentoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
class HybridDocumentStore {
    private static final Logger LOGGER = LoggerFactory.getLogger(HybridDocumentStore.class);

    private final DocumentoRepository documentos;
    private final BlobStorageService blobStorage;
    private final CapturedDocumentPublisher publisher;

    HybridDocumentStore(DocumentoRepository documentos, BlobStorageService blobStorage, CapturedDocumentPublisher publisher) {
        this.documentos = documentos;
        this.blobStorage = blobStorage;
        this.publisher = publisher;
    }

    boolean urlJaProcessada(String url) throws Exception {
        return documentos.urlJaFoiProcessada(url);
    }

    boolean persistirDocumento(
        ExecutarScraping comando,
        String url,
        String nomeArquivo,
        byte[] conteudo,
        TipoEvidencia tipoEvidencia,
        String observacao,
        Map<String, String> metadadosExtra) throws Exception {
        var nomeComExtensao = ensureExtension(nomeArquivo, ".pdf");
        var instrucoesCaptura = comando.instrucoesScrapingIa() == null ? "" : comando.instrucoesScrapingIa();
        if (observacao != null && !observacao.isBlank()) {
            instrucoesCaptura += "\nObservacao do scraper: " + observacao;
        }
        return persistirCore(comando, url, nomeComExtensao, conteudo, tipoEvidencia, instrucoesCaptura, metadadosExtra, true);
    }

    boolean persistirConteudoTexto(
        ExecutarScraping comando,
        String url,
        String nomeArquivo,
        String conteudoTexto,
        TipoEvidencia tipoEvidencia,
        Map<String, String> metadadosExtra) throws Exception {
        return persistirCore(
            comando,
            url,
            nomeArquivo,
            conteudoTexto.getBytes(StandardCharsets.UTF_8),
            tipoEvidencia,
            comando.instrucoesScrapingIa() == null ? "" : comando.instrucoesScrapingIa(),
            metadadosExtra,
            false);
    }

    String uploadAuditBlob(ExecutarScraping comando, String nomeArquivo, byte[] conteudo) throws Exception {
        var stagingPath = StagingPathBuilder.buildLocalStagingPath(comando);
        return blobStorage.uploadStaging(conteudo, stagingPath + "/auditoria", nomeArquivo);
    }

    private boolean persistirCore(
        ExecutarScraping comando,
        String url,
        String nomeArquivoFinal,
        byte[] conteudo,
        TipoEvidencia tipoEvidencia,
        String instrucoesCaptura,
        Map<String, String> metadadosExtra,
        boolean checkHash) throws Exception {
        var hash = ScraperUtilities.sha256(conteudo);
        if (documentos.urlJaFoiProcessada(url)) {
            LOGGER.debug("documento_dedup_url url={}", url);
            return false;
        }
        if (checkHash && documentos.arquivoJaFoiProcessado(hash)) {
            LOGGER.debug("documento_dedup_hash hash={} url={}", hash, url);
            return false;
        }

        // UUID unico para o documento logico: usado tanto no row do Cosmos quanto no DocumentoCapturado publicado.
        // Garante que o processing-engine faca upsert no MESMO id e nao crie um row duplicado.
        var documentoId = UUID.randomUUID();
        var blobName = checkHash ? hash + extension(nomeArquivoFinal) : nomeArquivoFinal;
        var stagingPath = StagingPathBuilder.buildLocalStagingPath(comando);

        // 1) Upload blob.
        var uploadStart = System.nanoTime();
        String uploadedUrl;
        try {
            uploadedUrl = blobStorage.uploadStaging(conteudo, stagingPath, blobName);
        } catch (Exception ex) {
            LOGGER.error("documento_upload_blob_falhou url={} blob={} path={}", url, blobName, stagingPath, ex);
            throw ex;
        }
        LOGGER.info("documento_upload_blob_ok url={} blob_url={} bytes={} duracao_ms={}",
            url, uploadedUrl, conteudo == null ? 0 : conteudo.length,
            (System.nanoTime() - uploadStart) / 1_000_000);

        // 2) Salvar Cosmos ANTES de publicar. Se falhar, compensa apagando o blob para nao deixar lixo.
        var documento = new Documento(
            documentoId,
            url,
            hash,
            comando.idFonteCaptacao(),
            tipoEvidencia,
            uploadedUrl,
            nomeArquivoFinal,
            "DocumentScraperHybrid",
            OffsetDateTime.now()
        );
        var saveStart = System.nanoTime();
        try {
            documentos.salvar(documento);
        } catch (Exception ex) {
            LOGGER.error("documento_cosmos_save_falhou url={} blob_url={} documento_id={} - compensando blob",
                url, uploadedUrl, documentoId, ex);
            try {
                blobStorage.delete(uploadedUrl);
            } catch (Exception compensationEx) {
                LOGGER.warn("documento_blob_compensacao_falhou blob_url={}", uploadedUrl, compensationEx);
            }
            throw ex;
        }
        LOGGER.info("documento_cosmos_save_ok url={} documento_id={} duracao_ms={}",
            url, documentoId, (System.nanoTime() - saveStart) / 1_000_000);

        // 3) Publicar evento. Se falhar, registro ja existe em Cosmos+Blob; um reaper futuro pode reprocessar.
        var metadados = new HashMap<String, String>();
        if (comando.metadados() != null) {
            metadados.putAll(comando.metadados());
        }
        if (metadadosExtra != null) {
            metadados.putAll(metadadosExtra);
        }
        var capturado = new DocumentoCapturado(
            documentoId,
            url,
            uploadedUrl,
            instrucoesCaptura,
            hash,
            comando.idFonteCaptacao(),
            tipoEvidencia,
            metadados,
            OffsetDateTime.now());
        var publishStart = System.nanoTime();
        try {
            publisher.publicar(capturado);
        } catch (Exception ex) {
            LOGGER.error("documento_publish_falhou url={} documento_id={} - registro persistido em Cosmos+Blob, evento nao publicado",
                url, documentoId, ex);
            throw ex;
        }
        LOGGER.info("documento_publish_ok url={} documento_id={} duracao_ms={}",
            url, documentoId, (System.nanoTime() - publishStart) / 1_000_000);
        return true;
    }

    private static String ensureExtension(String name, String defaultExtension) {
        return extension(name).isBlank() ? name + defaultExtension : name;
    }

    private static String extension(String name) {
        var index = name == null ? -1 : name.lastIndexOf('.');
        return index >= 0 ? name.substring(index) : "";
    }
}