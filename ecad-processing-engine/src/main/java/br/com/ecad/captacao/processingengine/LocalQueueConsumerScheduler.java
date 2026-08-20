package br.com.ecad.captacao.processingengine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class LocalQueueConsumerScheduler {
    private static final Logger LOGGER = LoggerFactory.getLogger(LocalQueueConsumerScheduler.class);

    private final LocalQueueConsumerService consumer;
    private final ConsumerState state;
    private final br.com.ecad.captacao.shared.infrastructure.local.LocalDevelopmentSettings localDevelopment;
    private final boolean enabled;

    LocalQueueConsumerScheduler(
        LocalQueueConsumerService consumer,
        ConsumerState state,
        br.com.ecad.captacao.shared.infrastructure.local.LocalDevelopmentSettings localDevelopment,
        @Value("${PROCESSING_ENGINE_CONSUMER_ENABLED:true}") boolean enabled) {
        this.consumer = consumer;
        this.state = state;
        this.localDevelopment = localDevelopment;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "${PROCESSING_ENGINE_LOCAL_POLL_INTERVAL_MS:1000}")
    void poll() {
        if (!enabled || !localDevelopment.enabled) {
            return;
        }

        state.setConsumerRunning(true);
        try {
            consumer.consumeOnce();
        } catch (Exception ex) {
            LOGGER.error("Erro ao consumir fila local do ProcessingEngine.", ex);
        }
    }
}
