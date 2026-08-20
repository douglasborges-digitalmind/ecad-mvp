package br.com.ecad.captacao.processingengine;

import org.springframework.stereotype.Component;

@Component
class ConsumerState implements ProcessingEngineMonitor {
    private volatile boolean consumerRunning;

    @Override
    public boolean isConsumerRunning() {
        return consumerRunning;
    }

    void setConsumerRunning(boolean consumerRunning) {
        this.consumerRunning = consumerRunning;
    }
}
