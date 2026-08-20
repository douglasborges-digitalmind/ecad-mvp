package br.com.ecad.captacao.processingengine;

import java.io.IOException;
import java.net.SocketTimeoutException;

import br.com.ecad.captacao.shared.domain.exceptions.BlobStorageException;
import br.com.ecad.captacao.shared.domain.exceptions.ExtractionException;
import br.com.ecad.captacao.shared.domain.exceptions.ProcessingException;

/**
 * Classificador de falhas do pipeline.
 *
 * <p>Substitui o antigo metodo {@code classificarFalha(Exception)} do {@code DefaultProcessingPipeline},
 * que tinha ~80 linhas de {@code instanceof} + {@code String.contains}. Agora a logica vive em
 * um unico lugar, e cada categoria e estavel (constantes) em vez de magic strings espalhadas.
 *
 * <p>As categorias sao desenhadas para alimentar o campo {@code resultado} da metrica
 * operacional {@code MetricaExecucaoOperacional} — textos estaveis para que dashboards
 * possam agregar.
 */
final class FaultClassifier {

    static final String FALHA_BLOBAUSENTE = "falha_blob_ausente";
    static final String FALHA_BLOBTIMEOUT = "falha_blob_timeout";
    static final String FALHA_BLOBSTORAGE = "falha_blob_storage";
    static final String FALHA_CRITERIOAUSENTE = "falha_criterio_ausente";
    static final String FALHA_RESPOSTAIAINVALIDA = "falha_resposta_ia_invalida";
    static final String FALHA_EXTRACAOIA = "falha_extracao_ia";
    static final String FALHA_FONTEERROCOSMOS = "falha_fonte_erro_cosmos";
    static final String FALHA_SGAVERIFICACAO = "falha_sga_verificacao";
    static final String FALHA_PERSISTENCIAEVENTO = "falha_persistencia_evento";
    static final String FALHA_CRITERIOERROCOSMOS = "falha_criterio_erro_cosmos";
    static final String FALHA_TIMEOUT = "falha_timeout";
    static final String FALHA_PROCESSING = "falha_processing";
    static final String FALHA_INESPERADA = "falha_inesperada";

    private FaultClassifier() {
    }

    /**
     * Classifica uma excecao do pipeline em (categoria, detalhe). O detalhe e a mensagem
     * original (ou da causa raiz, quando disponivel) para diagnostico operacional.
     */
    static Classification classificar(Exception ex) {
        if (ex instanceof BlobStorageException blob) {
            return classificarBlob(blob);
        }
        if (ex instanceof ExtractionException extraction) {
            return classificarExtracao(extraction);
        }
        if (ex instanceof ProcessingException processing) {
            return classificarProcessing(processing);
        }
        return new Classification(FALHA_INESPERADA, rootMessage(ex));
    }

    private static Classification classificarBlob(BlobStorageException ex) {
        var lower = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
        if (lower.contains("404") || lower.contains("not found") || lower.contains("exist") || lower.contains("inexistente")) {
            return new Classification(FALHA_BLOBAUSENTE, ex.getMessage());
        }
        if (lower.contains("timeout") || lower.contains("timed out") || lower.contains("read timed out")) {
            return new Classification(FALHA_BLOBTIMEOUT, ex.getMessage());
        }
        return new Classification(FALHA_BLOBSTORAGE, ex.getMessage());
    }

    private static Classification classificarExtracao(ExtractionException ex) {
        var msg = ex.getMessage();
        if (msg != null && msg.contains("Criterio de extracao nao encontrado")) {
            return new Classification(FALHA_CRITERIOAUSENTE, msg);
        }
        if (msg != null && (msg.contains("resposta invalida") || msg.contains("JSON"))) {
            return new Classification(FALHA_RESPOSTAIAINVALIDA, msg);
        }
        return new Classification(FALHA_EXTRACAOIA, msg);
    }

    private static Classification classificarProcessing(ProcessingException ex) {
        var msg = ex.getMessage();
        var causeMsg = ex.getCause() != null ? ex.getCause().getMessage() : null;
        if (msg != null) {
            if (msg.contains("buscar fonte de captacao")) {
                return new Classification(FALHA_FONTEERROCOSMOS, msg);
            }
            if (msg.contains("verificar SGA") || msg.contains("Falha no SGA")) {
                return new Classification(FALHA_SGAVERIFICACAO, msg);
            }
            if (msg.contains("persistir evento") || msg.contains("Falha ao persistir")) {
                return new Classification(FALHA_PERSISTENCIAEVENTO, msg);
            }
            if (msg.contains("obter criterio")) {
                return new Classification(FALHA_CRITERIOERROCOSMOS, msg);
            }
            if (msg.contains("extracao IA") || msg.contains("IA")) {
                return new Classification(FALHA_EXTRACAOIA, withRootCause(ex, msg));
            }
            if (msg.contains("blob storage") || msg.contains("Falha no blob")) {
                return new Classification(FALHA_BLOBSTORAGE, msg);
            }
        }
        if (ex.getCause() instanceof SocketTimeoutException) {
            return new Classification(FALHA_TIMEOUT, causeMsg);
        }
        if (ex.getCause() instanceof IOException && causeMsg != null && causeMsg.toLowerCase().contains("timeout")) {
            return new Classification(FALHA_TIMEOUT, causeMsg);
        }
        return new Classification(FALHA_PROCESSING, msg);
    }

    private static String rootMessage(Exception ex) {
        var msg = ex.getMessage();
        if (msg == null && ex.getCause() != null) {
            return ex.getCause().getMessage();
        }
        return msg;
    }

    /**
     * Constrói um detalhe de falha combinando a mensagem da excecao com a causa raiz,
     * preservando o stack trace completo para diagnostico operacional.
     */
    private static String withRootCause(Exception ex, String msg) {
        var cause = ex.getCause();
        if (cause == null) {
            return msg;
        }
        var causeMsg = cause.getMessage();
        if (causeMsg == null || causeMsg.isBlank()) {
            causeMsg = cause.getClass().getSimpleName();
        }
        if (msg == null || msg.isBlank()) {
            return causeMsg;
        }
        return msg + " -> " + cause.getClass().getSimpleName() + ": " + causeMsg;
    }

    record Classification(String resultado, String falhaDetalhe) {
    }
}
