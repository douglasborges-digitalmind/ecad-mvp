package br.com.ecad.captacao.controlcenter.services;

import java.time.Duration;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

import br.com.ecad.captacao.shared.contracts.DocumentoCapturado;
import br.com.ecad.captacao.shared.contracts.ExecutarScraping;
import br.com.ecad.captacao.shared.contracts.KeysMetadados;
import br.com.ecad.captacao.shared.contracts.Routes;
import br.com.ecad.captacao.shared.domain.enums.TipoEvidencia;
import br.com.ecad.captacao.shared.infrastructure.local.LocalDevelopmentSettings;
import br.com.ecad.captacao.shared.infrastructure.local.LocalMessageQueue;
import br.com.ecad.captacao.shared.infrastructure.local.LocalServiceInstanceRegistry;

public class LocalQueuePublisher implements EventPublisher {
    private static final Duration PROCESSING_ENGINE_INSTANCE_STALE_AFTER = Duration.ofSeconds(15);

    private final LocalMessageQueue messageQueue;
    private final LocalServiceInstanceRegistry serviceInstanceRegistry;
    private final boolean localDevelopmentEnabled;

    public LocalQueuePublisher(LocalMessageQueue messageQueue, LocalServiceInstanceRegistry serviceInstanceRegistry, LocalDevelopmentSettings localDevelopment) {
        this.messageQueue = messageQueue;
        this.serviceInstanceRegistry = serviceInstanceRegistry;
        this.localDevelopmentEnabled = localDevelopment != null && localDevelopment.enabled;
    }

    @Override
    public void publicarExecutarScraping(ExecutarScraping comando) throws java.io.IOException {
        var route = resolveRoute(comando);
        messageQueue.enqueue("scraping_commands", route, comando);
    }

    @Override
    public void publicarDocumentoCapturado(DocumentoCapturado documento) throws java.io.IOException {
        messageQueue.enqueue("captured_documents", resolveProcessingEngineRoute(documento), documento);
    }

    private String resolveProcessingEngineRoute(DocumentoCapturado documento) throws java.io.IOException {
        var routes = serviceInstanceRegistry.listActiveProcessingEngineRoutes(PROCESSING_ENGINE_INSTANCE_STALE_AFTER);
        if (routes.isEmpty()) {
            routes = List.of(Routes.PROCESSING_ENGINE);
        }
        if (routes.size() == 1) {
            return routes.getFirst();
        }

        var bucket = Math.floorMod(documento.idFonteCaptacao().hashCode(), routes.size());
        return routes.get(bucket);
    }

    private static String resolveRoute(ExecutarScraping comando) {
        return Routes.DOCUMENT_SCRAPER;
    }
}
