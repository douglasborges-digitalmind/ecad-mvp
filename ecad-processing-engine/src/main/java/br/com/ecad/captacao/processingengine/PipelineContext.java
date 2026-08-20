package br.com.ecad.captacao.processingengine;

import java.util.List;
import java.util.UUID;

import br.com.ecad.captacao.shared.contracts.DocumentoCapturado;
import br.com.ecad.captacao.shared.domain.entities.CriterioExtracao;
import br.com.ecad.captacao.shared.domain.entities.FonteCaptacao;
import br.com.ecad.captacao.shared.domain.entities.MetricaExecucaoIA;
import br.com.ecad.captacao.shared.domain.enums.StatusSGA;

/**
 * Contexto mutável compartilhado entre os passos do pipeline.
 */
class PipelineContext {
    final DocumentoCapturado documento;
    final UUID operationId;
    final long startedNanos;

    // Acumulado pelos passos
    CriterioExtracao criterio;
    ExtractionExecutionResult extraction;
    FonteCaptacao fonte;
    List<MetricaExecucaoIA> metricas;
    StatusSGA statusSga;
    String urlProducao;
    String linkFonte;

    PipelineContext(DocumentoCapturado documento, UUID operationId, long startedNanos) {
        this.documento = documento;
        this.operationId = operationId;
        this.startedNanos = startedNanos;
    }

    long elapsedMs() {
        return Math.max(0, (System.nanoTime() - startedNanos) / 1_000_000);
    }
}