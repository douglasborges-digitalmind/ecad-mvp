package br.com.ecad.captacao.shared.infrastructure.quarantine;

import java.time.OffsetDateTime;
import java.util.Map;

public record FailedMessageContext(
    int attemptCount,
    OffsetDateTime firstFailureUtc,
    OffsetDateTime lastFailureUtc,
    String correlationId,
    Map<String, String> metadata) {
}