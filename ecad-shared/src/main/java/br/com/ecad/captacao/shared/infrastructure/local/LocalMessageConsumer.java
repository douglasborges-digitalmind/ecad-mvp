package br.com.ecad.captacao.shared.infrastructure.local;

import java.io.IOException;

import br.com.ecad.captacao.shared.infrastructure.messaging.MessageConsumer;

/**
 * Implementação local de {@link MessageConsumer} que delega para {@link LocalMessageQueue#consumeAvailable}.
 */
public class LocalMessageConsumer implements MessageConsumer {
    private final LocalMessageQueue queue;
    private final String defaultRoute;
    private volatile boolean running;

    public LocalMessageConsumer(LocalMessageQueue queue, String defaultRoute) {
        this.queue = queue;
        this.defaultRoute = defaultRoute;
    }

    @Override
    public <T> void start(String topic, String route, Class<T> payloadType, MessageHandler<T> handler) throws IOException {
        this.running = true;
        // O consumo local é tipicamente feito por scheduler, não por thread contínua.
        // Este método existe para conformidade com a interface.
    }

    @Override
    public void stop() {
        this.running = false;
    }

    @Override
    public void checkpoint() {
        // LocalMessageQueue não tem checkpoint persistido — as mensagens são arquivos.
    }

    public <T> int consumeAvailable(String topic, String route, Class<T> payloadType, MessageHandler<T> handler) throws Exception {
        return queue.consumeAvailable(topic, route, payloadType, payload -> {
            try {
                handler.handle(payload);
            } catch (Exception e) {
                if (e instanceof RuntimeException re) throw re;
                throw new RuntimeException(e);
            }
        });
    }
}