package br.com.ecad.captacao.shared.infrastructure.messaging;

import java.io.IOException;

/**
 * Interface neutra de publicação de mensagens (Event Hubs / Kafka / fila local).
 *
 * <p>Substitui {@code AzureEventHubPublisher} e {@code LocalMessageQueue.enqueue}
 * com um contrato único para publicação.</p>
 */
public interface MessagePublisher {

    /**
     * Publica um payload serializável (JSON) em um tópico/fila.
     *
     * @param topic   nome do tópico (ex: "scraping_commands", "captured_documents")
     * @param payload objeto a ser serializado como JSON
     */
    void publish(String topic, Object payload) throws IOException;
}