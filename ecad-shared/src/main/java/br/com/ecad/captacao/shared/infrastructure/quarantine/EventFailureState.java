package br.com.ecad.captacao.shared.infrastructure.quarantine;

import java.time.OffsetDateTime;

public record EventFailureState(int attemptCount, OffsetDateTime firstFailureUtc, OffsetDateTime lastFailureUtc) {
}