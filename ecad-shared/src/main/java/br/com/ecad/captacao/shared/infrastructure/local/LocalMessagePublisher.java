package br.com.ecad.captacao.shared.infrastructure.local;

import java.io.IOException;

import br.com.ecad.captacao.shared.JsonDefaults;
import br.com.ecad.captacao.shared.infrastructure.messaging.MessagePublisher;

/**
 * Implementação local de {@link MessagePublisher} que delega para {@link LocalMessageQueue}.
 */
public class LocalMessagePublisher implements MessagePublisher {
    private final LocalMessageQueue queue;
    private final String defaultRoute;

    public LocalMessagePublisher(LocalMessageQueue queue, String defaultRoute) {
        this.queue = queue;
        this.defaultRoute = defaultRoute;
    }

    @Override
    public void publish(String topic, Object payload) throws IOException {
        queue.enqueue(topic, defaultRoute, payload);
    }
}