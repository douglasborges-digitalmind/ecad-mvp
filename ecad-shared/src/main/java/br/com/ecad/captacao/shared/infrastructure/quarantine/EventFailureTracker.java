package br.com.ecad.captacao.shared.infrastructure.quarantine;

public interface EventFailureTracker {
    EventFailureState increment(String messageId);

    void clear(String messageId);
}