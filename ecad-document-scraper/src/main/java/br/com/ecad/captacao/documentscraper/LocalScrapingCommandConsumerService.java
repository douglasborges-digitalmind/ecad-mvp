package br.com.ecad.captacao.documentscraper;

import br.com.ecad.captacao.shared.contracts.ExecutarScraping;
import br.com.ecad.captacao.shared.infrastructure.local.LocalMessageQueue;
import org.springframework.stereotype.Service;

@Service
class LocalScrapingCommandConsumerService {
    private final DocumentScraperSettings settings;
    private final LocalMessageQueue queue;
    private final HybridScrapingPipeline pipeline;

    LocalScrapingCommandConsumerService(DocumentScraperSettings settings, LocalMessageQueue queue, HybridScrapingPipeline pipeline) {
        this.settings = settings;
        this.queue = queue;
        this.pipeline = pipeline;
    }

    int consumeOnce() throws Exception {
        return queue.consumeAvailable(settings.scrapingCommandsTopic(), settings.localConsumerRoute(), ExecutarScraping.class, pipeline::processarComando);
    }
}
