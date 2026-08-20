package br.com.ecad.captacao.processingengine;

import java.util.UUID;

import br.com.ecad.captacao.shared.contracts.DocumentoCapturado;
import br.com.ecad.captacao.shared.domain.exceptions.BlobStorageException;
import br.com.ecad.captacao.shared.domain.exceptions.ExtractionException;
import br.com.ecad.captacao.shared.domain.exceptions.ProcessingException;
import br.com.ecad.captacao.shared.infrastructure.blob.BlobStorageService;
import br.com.ecad.captacao.shared.infrastructure.metrics.MetricsCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

/**
 * Pipeline de processamento de documentos capturados.
 * Orquestra a sequência de steps: Persist → Extraction → Enrichment → Validation → Blob → Event → Metrics.
 *
 * Cada step tem responsabilidade única. O pipeline gerencia o fluxo, pontos de saída antecipada
 * (critério ausente, resposta IA inválida, sem evento) e compensação de blob.
 */
@Service
class DefaultProcessingPipeline implements ProcessingPipeline {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultProcessingPipeline.class);

    private final PersistDocumentoStep persistStep;
    private final ExtractionStep extractionStep;
    private final EnrichmentStep enrichmentStep;
    private final SgaVerificationStep sgaStep;
    private final BlobPromotionStep blobStep;
    private final EventPersistenceStep eventStep;
    private final MetricsStep metricsStep;
    private final ProcessingOperationalMetricsService operationalMetrics;
    private final BlobStorageService blobStorage;
    private final MetricsCollector metricsCollector;

    DefaultProcessingPipeline(
        PersistDocumentoStep persistStep,
        ExtractionStep extractionStep,
        EnrichmentStep enrichmentStep,
        SgaVerificationStep sgaStep,
        BlobPromotionStep blobStep,
        EventPersistenceStep eventStep,
        MetricsStep metricsStep,
        ProcessingOperationalMetricsService operationalMetrics,
        BlobStorageService blobStorage,
        MetricsCollector metricsCollector) {
        this.persistStep = persistStep;
        this.extractionStep = extractionStep;
        this.enrichmentStep = enrichmentStep;
        this.sgaStep = sgaStep;
        this.blobStep = blobStep;
        this.eventStep = eventStep;
        this.metricsStep = metricsStep;
        this.operationalMetrics = operationalMetrics;
        this.blobStorage = blobStorage;
        this.metricsCollector = metricsCollector;
    }

    @Override
    public void processar(DocumentoCapturado documento) throws ProcessingException {
        var started = System.nanoTime();
        var operationId = UUID.randomUUID();
        var docId = documento.id() == null ? "n/a" : documento.id().toString();

        // MDC: correlation_id é propagado do consumer (messageId do Event Hub).
        // Se já definido pelo handler, mantém; senão, usa o operationId como fallback.
        try (var ignoredDoc = MDC.putCloseable("documento_id", docId)) {
            var existing = MDC.get("correlation_id");
            if (existing == null) {
                try (var ignoredOp = MDC.putCloseable("correlation_id", operationId.toString())) {
                    processarComContexto(new PipelineContext(documento, operationId, started));
                }
            } else {
                processarComContexto(new PipelineContext(documento, operationId, started));
            }
        }
    }

    private void salvarResultado(PipelineContext ctx, String resultado, boolean sucesso, int itens, long elapsed) {
        salvarResultado(ctx, resultado, sucesso, itens, elapsed, null);
    }

    private void salvarResultado(PipelineContext ctx, String resultado, boolean sucesso, int itens, long elapsed, String falhaDetalhe) {
        try {
            operationalMetrics.salvarResultadoPipeline(ctx.operationId, ctx.documento.idFonteCaptacao(), resultado, sucesso, itens, elapsed, falhaDetalhe);
        } catch (Exception e) {
            LOGGER.warn("Falha ao salvar metrica operacional do pipeline. opId={} resultado={}", ctx.operationId, resultado, e);
        }
    }

    private void processarComContexto(PipelineContext ctx) throws ProcessingException {

        try {
            // Step 1: Persistir documento capturado
            LOGGER.debug("Pipeline iniciando: step=persist");
            metricsCollector.recordStep("persist", () -> { persistStep.execute(ctx); return null; });

            // Step 2: Extração via IA
            LOGGER.debug("Pipeline: step=extraction");
            metricsCollector.recordStep("extraction", () -> { extractionStep.execute(ctx); return null; });
            ctx.metricas = ctx.extraction.metricas();

            // Step 2b: Enriquecimento
            metricsCollector.recordStep("enrichment", () -> { enrichmentStep.execute(ctx); return null; });

            // Saída antecipada: resposta IA inválida
            if (ctx.extraction.status() == ExtractionExecutionStatus.INVALID_AI_RESPONSE) {
                metricsCollector.recordStep("metrics_early_exit", () -> { metricsStep.execute(ctx); return null; });
                var detail = ctx.extraction.failureDetail() == null
                    ? "A IA retornou uma resposta invalida para extracao." : ctx.extraction.failureDetail();
                throw new ExtractionException(detail);
            }

            // Saída antecipada: sem evento detectado → descarte + delete blob
            if (ctx.extraction.status() == ExtractionExecutionStatus.NO_EVENT) {
                for (var m : ctx.metricas) {
                    m.resultadoDescarte = true;
                }
                metricsCollector.recordStep("metrics_discard", () -> { metricsStep.execute(ctx); return null; });
                metricsCollector.recordStep("blob_delete_staging", () -> {
                    try { blobStorage.delete(ctx.documento.urlStagingInterno()); } catch (BlobStorageException ignored) { }
                    return null;
                });
                salvarResultado(ctx, "descartado_sem_evento", true, 0, ctx.elapsedMs());
                return;
            }

            // Step 3: Salvar métricas preliminares
            metricsCollector.recordStep("metrics_save", () -> { metricsStep.execute(ctx); return null; });

            // Step 4: Verificação SGA
            LOGGER.debug("Pipeline: step=sga_verification");
            metricsCollector.recordStep("sga_verification", () -> { sgaStep.execute(ctx); return null; });

            // Step 5: Mover blob para produção + persistir evento + resolver link
            LOGGER.debug("Pipeline: step=blob_promotion");
            metricsCollector.recordStep("blob_promotion", () -> { blobStep.execute(ctx); return null; });
            // Steps 5b+6: Resolver link e persistir evento
            metricsCollector.recordStep("event_persistence", () -> { eventStep.execute(ctx); return null; });
            salvarResultado(ctx, "sucesso", true, 1, ctx.elapsedMs());
        } catch (BlobStorageException | ExtractionException ex) {
            var cf = FaultClassifier.classificar(ex);
            salvarResultado(ctx, cf.resultado(), false, 0, ctx.elapsedMs(), cf.falhaDetalhe());
            LOGGER.error("Pipeline falhou: resultado={} opId={} documentoId={} falhaDetalhe={}", cf.resultado(), ctx.operationId, ctx.documento.id(), cf.falhaDetalhe(), ex);
            if (ctx.urlProducao != null && !ctx.urlProducao.isBlank()) {
                try { blobStorage.delete(ctx.urlProducao); } catch (BlobStorageException ignored) { }
            }
            throw ex instanceof BlobStorageException ? new ProcessingException("Falha no blob storage", ex) : ex;
        } catch (ProcessingException ex) {
            var cf = FaultClassifier.classificar(ex);
            salvarResultado(ctx, cf.resultado(), false, 0, ctx.elapsedMs(), cf.falhaDetalhe());
            LOGGER.error("Pipeline falhou: resultado={} opId={} documentoId={} falhaDetalhe={}", cf.resultado(), ctx.operationId, ctx.documento.id(), cf.falhaDetalhe(), ex);
            if (ctx.urlProducao != null && !ctx.urlProducao.isBlank()) {
                try { blobStorage.delete(ctx.urlProducao); } catch (BlobStorageException ignored) { }
            }
            throw ex;
        } catch (Exception ex) {
            var cf = FaultClassifier.classificar(ex);
            salvarResultado(ctx, cf.resultado(), false, 0, ctx.elapsedMs(), cf.falhaDetalhe());
            LOGGER.error("Pipeline falhou: resultado={} opId={} documentoId={} falhaDetalhe={}", cf.resultado(), ctx.operationId, ctx.documento.id(), cf.falhaDetalhe(), ex);
            if (ctx.urlProducao != null && !ctx.urlProducao.isBlank()) {
                try { blobStorage.delete(ctx.urlProducao); } catch (BlobStorageException ignored) { }
            }
            throw new ProcessingException("Erro inesperado no pipeline", ex);
        } finally {
            MDC.clear();
        }

        LOGGER.debug("Pipeline concluido com sucesso: opId={} elapsedMs={}", ctx.operationId, ctx.elapsedMs());
    }
}