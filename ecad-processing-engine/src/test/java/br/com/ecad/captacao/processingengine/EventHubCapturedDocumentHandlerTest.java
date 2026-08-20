package br.com.ecad.captacao.processingengine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

import br.com.ecad.captacao.shared.JsonDefaults;
import br.com.ecad.captacao.shared.contracts.DocumentoCapturado;
import br.com.ecad.captacao.shared.infrastructure.config.AiProviderSettings;
import br.com.ecad.captacao.shared.domain.enums.TipoEvidencia;
import br.com.ecad.captacao.shared.infrastructure.quarantine.EventFailureTracker;
import br.com.ecad.captacao.shared.infrastructure.quarantine.FailedMessageContext;
import br.com.ecad.captacao.shared.infrastructure.quarantine.FailedMessageSink;
import br.com.ecad.captacao.shared.infrastructure.quarantine.InMemoryEventFailureTracker;
import org.junit.jupiter.api.Test;

class CapturedDocumentHandlerTest {
    @Test
    void enviaPayloadInvalidoParaQuarentenaEAtualizaCheckpoint() throws Exception {
        var sink = new RecordingFailedMessageSink();
        var tracker = new InMemoryEventFailureTracker();
        var checkpoint = new CountingCheckpoint();
        var handler = handler(false, sink, tracker);

        handler.handle("{ payload invalido", "msg-1", Map.of("partition", "0"), checkpoint::increment);

        assertThat(sink.messages).hasSize(1);
        assertThat(sink.messages.get(0).reason).contains("Payload invalido");
        assertThat(checkpoint.count).isEqualTo(1);
    }

    @Test
    void reprocessaAteLimiteDepoisMoveParaQuarentenaECheckpoint() throws Exception {
        var sink = new RecordingFailedMessageSink();
        var tracker = new InMemoryEventFailureTracker();
        var checkpoint = new CountingCheckpoint();
        var handler = handler(true, sink, tracker);
        var payload = JsonDefaults.objectMapper().writeValueAsString(documento());

        assertThatThrownBy(() -> handler.handle(payload, "msg-2", Map.of("partition", "1"), checkpoint::increment))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Falha simulada");
        handler.handle(payload, "msg-2", Map.of("partition", "1"), checkpoint::increment);

        assertThat(sink.messages).hasSize(1);
        assertThat(sink.messages.get(0).context().attemptCount()).isEqualTo(2);
        assertThat(checkpoint.count).isEqualTo(1);
    }

    private static CapturedDocumentHandler handler(boolean failingPipeline, FailedMessageSink sink, EventFailureTracker tracker) {
        var settings = settings(2, "ollama");
        ProcessingPipeline pipeline = documento -> {
            if (failingPipeline) throw new IllegalStateException("Falha simulada");
        };
        return new CapturedDocumentHandler(settings, pipeline, sink, tracker);
    }

    private static ProcessingEngineSettings settings(int maxAttempts, String aiProviderChain) {
        return new ProcessingEngineSettings(
            "", "", "", "", "", "", "captured_documents", "processing-engine", "processing-engine", 5,
            "cg-processing-engine",
            "", "ecad-captacao",
            "", "",  // azureStorageConnectionString, azureBlobContainerName
            "staging/", "producao/",
            maxAttempts, new AiProviderSettings(aiProviderChain, "", "https://openrouter.ai/api/v1", "google/gemini-3.1-flash-lite-preview", "", "gemini-3.1-flash-lite-preview", "http://localhost:11434", "llama3.2-vision:11b", "", "", "", ""),
            false, "", "", "", "", "", "", 10, 3, 3_600_000, 1);
    }

    private static DocumentoCapturado documento() {
        return new DocumentoCapturado(
            UUID.randomUUID(), "https://origem/documento.pdf",
            "https://blob/staging/documento.pdf", "capturar", "hash",
            UUID.randomUUID(), TipoEvidencia.CONTRATO_MUSICAL, Map.of(),
            OffsetDateTime.parse("2024-01-02T03:04:05Z"));
    }

    private static class RecordingFailedMessageSink implements FailedMessageSink {
        private final ArrayList<RecordedFailedMessage> messages = new ArrayList<>();
        @Override
        public void store(String component, String messageId, String payload, String reason, FailedMessageContext context) {
            messages.add(new RecordedFailedMessage(component, messageId, payload, reason, context));
        }
    }

    private static class CountingCheckpoint { private int count; void increment() { count++; } }
    private record RecordedFailedMessage(String component, String messageId, String payload, String reason, FailedMessageContext context) {}
}
