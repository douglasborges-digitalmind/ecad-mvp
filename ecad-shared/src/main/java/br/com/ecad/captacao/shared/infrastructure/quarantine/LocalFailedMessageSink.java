package br.com.ecad.captacao.shared.infrastructure.quarantine;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import br.com.ecad.captacao.shared.JsonDefaults;
import com.fasterxml.jackson.annotation.JsonProperty;

public class LocalFailedMessageSink implements FailedMessageSink {
    private static final DateTimeFormatter FILE_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final Path rootPath;

    public LocalFailedMessageSink(Path rootPath) {
        this.rootPath = rootPath;
    }

    @Override
    public void store(String component, String messageId, String payload, String reason, FailedMessageContext context) throws Exception {
        var directory = rootPath.resolve("failed-messages").resolve(sanitize(component));
        Files.createDirectories(directory);
        var file = directory.resolve(FILE_TIMESTAMP.format(OffsetDateTime.now(ZoneOffset.UTC)) + "_" + sanitize(messageId) + ".json");
        Files.writeString(file, JsonDefaults.objectMapper().writeValueAsString(envelope(component, messageId, payload, reason, context)), StandardCharsets.UTF_8);
    }

    static FailedMessageEnvelope envelope(String component, String messageId, String payload, String reason, FailedMessageContext context) {
        return new FailedMessageEnvelope(
            component,
            messageId,
            reason,
            payload,
            context.attemptCount(),
            context.firstFailureUtc(),
            context.lastFailureUtc(),
            context.correlationId(),
            context.metadata() == null ? Map.of() : context.metadata(),
            OffsetDateTime.now(ZoneOffset.UTC));
    }

    public static String sanitize(String value) {
        return value == null ? "" : value.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    public record FailedMessageEnvelope(
        @JsonProperty("Component") String component,
        @JsonProperty("MessageId") String messageId,
        @JsonProperty("Reason") String reason,
        @JsonProperty("Payload") String payload,
        @JsonProperty("AttemptCount") int attemptCount,
        @JsonProperty("FirstFailureUtc") OffsetDateTime firstFailureUtc,
        @JsonProperty("LastFailureUtc") OffsetDateTime lastFailureUtc,
        @JsonProperty("CorrelationId") String correlationId,
        @JsonProperty("Metadata") Map<String, String> metadata,
        @JsonProperty("TimestampUtc") OffsetDateTime timestampUtc) {
    }
}