package br.com.ecad.captacao.processingengine;

import br.com.ecad.captacao.shared.contracts.DocumentoCapturado;
import br.com.ecad.captacao.shared.infrastructure.local.LocalMessageQueue;
import org.springframework.stereotype.Service;

@Service
class LocalQueueConsumerService {
    private final ProcessingEngineSettings settings;
    private final LocalMessageQueue messageQueue;
    private final ProcessingPipeline pipeline;

    LocalQueueConsumerService(ProcessingEngineSettings settings, LocalMessageQueue messageQueue, ProcessingPipeline pipeline) {
        this.settings = settings;
        this.messageQueue = messageQueue;
        this.pipeline = pipeline;
    }

    int consumeOnce() throws Exception {
        return messageQueue.consumeAvailable(
            settings.capturedDocumentsTopic(),
            settings.localConsumerRoute(),
            DocumentoCapturado.class,
            pipeline::processar);
    }
}
