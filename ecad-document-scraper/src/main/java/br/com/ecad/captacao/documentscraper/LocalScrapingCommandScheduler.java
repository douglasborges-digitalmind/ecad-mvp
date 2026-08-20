package br.com.ecad.captacao.documentscraper;

import br.com.ecad.captacao.shared.common.SingleflightScheduler;
import br.com.ecad.captacao.shared.infrastructure.local.LocalDevelopmentSettings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class LocalScrapingCommandScheduler extends SingleflightScheduler {
    private final LocalScrapingCommandConsumerService service;
    private final LocalDevelopmentSettings localDevelopment;
    private final DocumentScraperConsumerState state;
    private final boolean enabled;

    LocalScrapingCommandScheduler(
        LocalScrapingCommandConsumerService service,
        LocalDevelopmentSettings localDevelopment,
        DocumentScraperConsumerState state,
        @Value("${DOCUMENT_SCRAPER_CONSUMER_ENABLED:true}") boolean enabled) {
        super("LocalScrapingCommandScheduler");
        this.service = service;
        this.localDevelopment = localDevelopment;
        this.state = state;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "${DOCUMENT_SCRAPER_CONSUMER_DELAY_MS:1000}")
    void poll() {
        if (!enabled || !localDevelopment.enabled) {
            state.setConsumerRunning(false);
            return;
        }
        runSafely();
    }

    @Override
    protected void execute() throws Exception {
        state.setConsumerRunning(true);
        service.consumeOnce();
    }
}
