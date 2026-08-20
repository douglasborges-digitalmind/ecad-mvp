package br.com.ecad.captacao.controlcenter.services;

import java.io.IOException;

import br.com.ecad.captacao.shared.contracts.DocumentoCapturado;
import br.com.ecad.captacao.shared.contracts.ExecutarScraping;
import br.com.ecad.captacao.shared.infrastructure.messaging.MessagePublisher;

public class CloudEventPublisher implements EventPublisher {
    private final MessagePublisher scrapingCommandPublisher;
    private final MessagePublisher capturedDocumentPublisher;
    private final String scrapingCommandsTopic;
    private final String capturedDocumentsTopic;

    public CloudEventPublisher(
            MessagePublisher scrapingCommandPublisher,
            MessagePublisher capturedDocumentPublisher,
            String scrapingCommandsTopic,
            String capturedDocumentsTopic) {
        this.scrapingCommandPublisher = scrapingCommandPublisher;
        this.capturedDocumentPublisher = capturedDocumentPublisher;
        this.scrapingCommandsTopic = scrapingCommandsTopic;
        this.capturedDocumentsTopic = capturedDocumentsTopic;
    }

    @Override
    public void publicarExecutarScraping(ExecutarScraping comando) throws IOException {
        scrapingCommandPublisher.publish(scrapingCommandsTopic, comando);
    }

    @Override
    public void publicarDocumentoCapturado(DocumentoCapturado documento) throws IOException {
        capturedDocumentPublisher.publish(capturedDocumentsTopic, documento);
    }
}