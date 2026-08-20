package br.com.ecad.captacao.processingengine;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;

import br.com.ecad.captacao.shared.JsonDefaults;
import br.com.ecad.captacao.shared.contracts.DocumentoCapturado;
import br.com.ecad.captacao.shared.contracts.Routes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

/**
 * Handler de documentos capturados recebidos via Kafka (ou Event Hubs, na versao anterior).
 * Substitui o antigo EventHubCapturedDocumentHandler.
 */
@Service
class CapturedDocumentHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(CapturedDocumentHandler.class);

    private final ProcessingEngineSettings settings;
    private final ProcessingPipeline pipeline;
    private final br.com.ecad.captacao.shared.infrastructure.quarantine.FailedMessageSink failedMessageSink;
    private final br.com.ecad.captacao.shared.infrastructure.quarantine.EventFailureTracker failureTracker;

    CapturedDocumentHandler(
        ProcessingEngineSettings settings,
        ProcessingPipeline pipeline,
        br.com.ecad.captacao.shared.infrastructure.quarantine.FailedMessageSink failedMessageSink,
        br.com.ecad.captacao.shared.infrastructure.quarantine.EventFailureTracker failureTracker) {
        this.settings = settings;
        this.pipeline = pipeline;
        this.failedMessageSink = failedMessageSink;
        this.failureTracker = failureTracker;
    }

    void handle(String payload, String messageId, Map<String, String> metadata, CheckpointAction checkpointAction) throws Exception {
        String correlationId = messageId;
        MDC.put("correlation_id", messageId);
        try {
            var documento = JsonDefaults.objectMapper().readValue(payload, DocumentoCapturado.class);
            if (documento == null) {
                quarantineAndCheckpoint(messageId, payload, "Payload invalido para DocumentoCapturado.", initialContext(messageId, metadata), checkpointAction);
                return;
            }

            correlationId = documento.id() == null ? messageId : documento.id().toString();
            MDC.put("documento_id", correlationId);
            pipeline.processar(documento);
            failureTracker.clear(messageId);
            checkpointAction.updateCheckpoint();
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            quarantineAndCheckpoint(messageId, payload, "Payload invalido para DocumentoCapturado.", initialContext(correlationId, metadata), checkpointAction);
        } catch (Exception ex) {
            var failure = failureTracker.increment(messageId);
            LOGGER.error(
                "Erro ao processar evento do ProcessingEngine. correlation_id={} attempt={} max_attempts={} metadata={}",
                correlationId,
                failure.attemptCount(),
                settings.maxEventProcessingAttempts(),
                metadata,
                ex);

            if (failure.attemptCount() >= settings.maxEventProcessingAttempts()) {
                quarantineAndCheckpoint(messageId, payload, ex.getMessage(), context(correlationId, metadata, failure), checkpointAction);
            } else {
                throw ex;
            }
        } finally {
            MDC.remove("documento_id");
            MDC.remove("correlation_id");
        }
    }

    private void quarantineAndCheckpoint(
        String messageId,
        String payload,
        String reason,
        br.com.ecad.captacao.shared.infrastructure.quarantine.FailedMessageContext context,
        CheckpointAction checkpointAction) throws Exception {
        failedMessageSink.store(Routes.PROCESSING_ENGINE, messageId, payload, reason, context);
        failureTracker.clear(messageId);
        checkpointAction.updateCheckpoint();
        LOGGER.warn("Mensagem encaminhada para quarentena no ProcessingEngine. correlation_id={} reason={}", context.correlationId(), reason);
    }

    private static br.com.ecad.captacao.shared.infrastructure.quarantine.FailedMessageContext initialContext(String correlationId, Map<String, String> metadata) {
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        return new br.com.ecad.captacao.shared.infrastructure.quarantine.FailedMessageContext(1, now, now, correlationId, metadata);
    }

    private static br.com.ecad.captacao.shared.infrastructure.quarantine.FailedMessageContext context(String correlationId, Map<String, String> metadata, br.com.ecad.captacao.shared.infrastructure.quarantine.EventFailureState failure) {
        return new br.com.ecad.captacao.shared.infrastructure.quarantine.FailedMessageContext(failure.attemptCount(), failure.firstFailureUtc(), failure.lastFailureUtc(), correlationId, metadata);
    }
}