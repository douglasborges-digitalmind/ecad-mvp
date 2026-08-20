package br.com.ecad.captacao.shared.infrastructure.quarantine;

public interface FailedMessageSink {
    void store(String component, String messageId, String payload, String reason, FailedMessageContext context) throws Exception;
}