package br.com.ecad.captacao.processingengine;

import br.com.ecad.captacao.shared.infrastructure.local.LocalDevelopmentSettings;
import br.com.ecad.captacao.shared.infrastructure.local.LocalServiceInstanceRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class LocalProcessingEngineHeartbeatScheduler {
    private static final Logger LOGGER = LoggerFactory.getLogger(LocalProcessingEngineHeartbeatScheduler.class);

    private final ProcessingEngineSettings settings;
    private final LocalDevelopmentSettings localDevelopment;
    private final LocalServiceInstanceRegistry registry;

    LocalProcessingEngineHeartbeatScheduler(
        ProcessingEngineSettings settings,
        LocalDevelopmentSettings localDevelopment,
        LocalServiceInstanceRegistry registry) {
        this.settings = settings;
        this.localDevelopment = localDevelopment;
        this.registry = registry;
    }

    @Scheduled(fixedDelayString = "${PROCESSING_ENGINE_LOCAL_HEARTBEAT_INTERVAL_MS:5000}")
    void heartbeat() {
        if (!localDevelopment.enabled) {
            return;
        }

        try {
            registry.registerProcessingEngineHeartbeat(settings.instanceId(), settings.localConsumerRoute());
        } catch (Exception ex) {
            LOGGER.warn("Nao foi possivel registrar heartbeat local do ProcessingEngine.", ex);
        }
    }
}