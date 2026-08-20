package br.com.ecad.captacao.shared.infrastructure.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/**
 * Coletor centralizado de métricas Micrometer para o ecossistema ECAD.
 * Instrumenta: pipeline steps, quarentena, e consumer lag.
 *
 * Uso:
 *   metricsCollector.recordStep("extraction", () -> step.execute(ctx));
 *   metricsCollector.incrementQuarantine("document-scraper");
 */
public class MetricsCollector {
    private final MeterRegistry registry;
    private final String componentTag;

    public MetricsCollector(MeterRegistry registry, String componentTag) {
        this.registry = registry;
        this.componentTag = componentTag;
    }

    /** Registra duração de um passo do pipeline com tags: componente, step, status. */
    public <T> T recordStep(String stepName, String status, Callable<T> action) throws Exception {
        var timer = Timer.builder("ecad.pipeline.step")
            .tag("componente", componentTag)
            .tag("step", stepName)
            .tag("status", status)
            .description("Tempo de execucao de cada passo do pipeline")
            .register(registry);
        return timer.recordCallable(action);
    }

    /** Registra duração com status inferido (sucesso=ok, falha=error). */
    public <T> T recordStep(String stepName, Callable<T> action) throws Exception {
        try {
            return recordStep(stepName, "ok", action);
        } catch (Exception e) {
            // Registra com status "error" para falhas
            Timer.builder("ecad.pipeline.step")
                .tag("componente", componentTag)
                .tag("step", stepName)
                .tag("status", "error")
                .register(registry)
                .record(0, TimeUnit.MILLISECONDS);
            throw e;
        }
    }

    /** Incrementa contador de mensagens enviadas para quarentena. */
    public void incrementQuarantine() {
        Counter.builder("ecad.quarantine.total")
            .tag("componente", componentTag)
            .description("Total de mensagens enviadas para quarentena")
            .register(registry)
            .increment();
    }

    /** Atualiza gauge de consumer lag (em mensagens). */
    public void setConsumerLag(String partition, long lag) {
        var tags = java.util.List.of(
            io.micrometer.core.instrument.Tag.of("componente", componentTag),
            io.micrometer.core.instrument.Tag.of("partition", partition));
        registry.gauge("ecad.consumer.lag", tags, lag);
    }

    /** Retorna o MeterRegistry subjacente para métricas customizadas. */
    public MeterRegistry registry() {
        return registry;
    }
}