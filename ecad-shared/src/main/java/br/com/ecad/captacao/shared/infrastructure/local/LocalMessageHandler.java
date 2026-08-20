package br.com.ecad.captacao.shared.infrastructure.local;

@FunctionalInterface
public interface LocalMessageHandler<T> {
    void handle(T payload) throws Exception;
}
