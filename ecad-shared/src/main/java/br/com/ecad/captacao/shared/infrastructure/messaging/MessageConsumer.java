package br.com.ecad.captacao.shared.infrastructure.messaging;

import java.io.IOException;

/**
 * Interface neutra de consumo de mensagens (Event Hubs / Kafka / fila local).
 *
 * <p>Substitui {@code EventProcessorClient} e {@code LocalMessageQueue.consumeAvailable}
 * com um contrato único para consumo com checkpoint.</p>
 */
public interface MessageConsumer {

    /** Callback para processamento de uma mensagem. */
    @FunctionalInterface
    interface MessageHandler<T> {
        void handle(T payload) throws Exception;
    }

    /**
     * Inicia o consumo de um tópico/fila.
     *
     * @param topic       nome do tópico
     * @param route       rota/consumer group
     * @param payloadType classe do payload esperado
     * @param handler     callback de processamento
     */
    <T> void start(String topic, String route, Class<T> payloadType, MessageHandler<T> handler) throws IOException;

    /**
     * Para o consumo (libera recursos).
     */
    void stop();

    /**
     * Persiste o checkpoint da posição atual.
     */
    void checkpoint();
}