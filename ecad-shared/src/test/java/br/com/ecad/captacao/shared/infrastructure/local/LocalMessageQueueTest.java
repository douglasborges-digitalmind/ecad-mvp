package br.com.ecad.captacao.shared.infrastructure.local;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import br.com.ecad.captacao.shared.contracts.DocumentoCapturado;
import br.com.ecad.captacao.shared.domain.enums.TipoEvidencia;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalMessageQueueTest {
    @TempDir
    Path tempDir;

    @Test
    void consumeAvailableShouldRequeueFailuresAndDeadLetterOnThirdAttempt() throws Exception {
        var settings = new LocalDevelopmentSettings(tempDir, true);
        var queue = new LocalMessageQueue(settings);
        var document = new DocumentoCapturado(
            UUID.randomUUID(),
            "https://example.org/doc.pdf",
            "staging/doc.pdf",
            "capturar",
            "abc",
            UUID.randomUUID(),
            TipoEvidencia.CONTRATO_MUSICAL,
            Map.of(),
            OffsetDateTime.parse("2026-04-01T12:00:00Z"));

        queue.enqueue("captured_documents", "processing-engine", document);

        try (var stream = Files.list(settings.getQueuePath("captured_documents", "processing-engine"))) {
            assertThat(stream.map(path -> path.getFileName().toString()).toList())
                .allMatch(name -> name.endsWith(".json"));
        }

        for (var attempt = 1; attempt <= 2; attempt++) {
            assertThatThrownBy(() -> queue.consumeAvailable(
                "captured_documents",
                "processing-engine",
                DocumentoCapturado.class,
                payload -> { throw new IllegalStateException("falha"); },
                1))
                .isInstanceOf(IllegalStateException.class);

            assertThat(countJson(settings.getQueuePath("captured_documents", "processing-engine").resolve(".deadletter"))).isZero();
            assertThat(countJson(settings.getQueuePath("captured_documents", "processing-engine"))).isEqualTo(1);
        }

        assertThatThrownBy(() -> queue.consumeAvailable(
            "captured_documents",
            "processing-engine",
            DocumentoCapturado.class,
            payload -> { throw new IllegalStateException("falha"); },
            1))
            .isInstanceOf(IllegalStateException.class);

        assertThat(countJson(settings.getQueuePath("captured_documents", "processing-engine"))).isZero();
        assertThat(countJson(settings.getQueuePath("captured_documents", "processing-engine").resolve(".deadletter"))).isEqualTo(1);
    }

    @Test
    void consumeAvailableShouldDeserializeAndDeleteProcessedFile() throws Exception {
        var settings = new LocalDevelopmentSettings(tempDir, true);
        var queue = new LocalMessageQueue(settings);
        var document = new DocumentoCapturado(
            UUID.randomUUID(),
            "https://example.org/doc.pdf",
            "staging/doc.pdf",
            "capturar",
            "abc",
            UUID.randomUUID(),
            TipoEvidencia.CONTRATO_MUSICAL,
            Map.of(),
            OffsetDateTime.parse("2026-04-01T12:00:00Z"));

        queue.enqueue("captured_documents", "processing-engine", document);

        var processed = queue.consumeAvailable(
            "captured_documents",
            "processing-engine",
            DocumentoCapturado.class,
            payload -> assertThat(payload.urlOrigem()).isEqualTo(document.urlOrigem()),
            1);

        assertThat(processed).isEqualTo(1);
        assertThat(countJson(settings.getQueuePath("captured_documents", "processing-engine"))).isZero();
    }

    private static long countJson(Path path) throws java.io.IOException {
        if (!Files.isDirectory(path)) {
            return 0;
        }

        try (var stream = Files.list(path)) {
            return stream.filter(file -> file.getFileName().toString().endsWith(".json")).count();
        }
    }
}
