package br.com.ecad.captacao.documentscraper;

import br.com.ecad.captacao.shared.contracts.DocumentoCapturado;
import br.com.ecad.captacao.shared.contracts.Routes;
import br.com.ecad.captacao.shared.infrastructure.local.LocalDevelopmentSettings;
import br.com.ecad.captacao.shared.infrastructure.local.LocalMessageQueue;
import org.springframework.stereotype.Service;

@Service
class DocumentScraperCapturedDocumentPublisher implements CapturedDocumentPublisher {
    private final DocumentScraperSettings settings;
    private final LocalDevelopmentSettings localDevelopment;
    private final LocalMessageQueue localQueue;
    private final DocumentScraperCloudClients cloudClients;

    DocumentScraperCapturedDocumentPublisher(
        DocumentScraperSettings settings,
        LocalDevelopmentSettings localDevelopment,
        LocalMessageQueue localQueue,
        DocumentScraperCloudClients cloudClients) {
        this.settings = settings;
        this.localDevelopment = localDevelopment;
        this.localQueue = localQueue;
        this.cloudClients = cloudClients;
    }

    @Override
    public void publicar(DocumentoCapturado documento) throws Exception {
        if (!localDevelopment.enabled && cloudClients.hasCapturedDocumentPublisher()) {
            cloudClients.capturedDocumentPublisher().publish(settings.capturedDocumentsTopic(), documento);
            return;
        }
        localQueue.enqueue(settings.capturedDocumentsTopic(), Routes.PROCESSING_ENGINE, documento);
    }
}
