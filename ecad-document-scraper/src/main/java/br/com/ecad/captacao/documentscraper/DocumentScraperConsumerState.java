package br.com.ecad.captacao.documentscraper;

import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.stereotype.Component;

@Component
class DocumentScraperConsumerState {
    private final AtomicBoolean consumerRunning = new AtomicBoolean();

    boolean isConsumerRunning() {
        return consumerRunning.get();
    }

    void setConsumerRunning(boolean running) {
        consumerRunning.set(running);
    }
}
