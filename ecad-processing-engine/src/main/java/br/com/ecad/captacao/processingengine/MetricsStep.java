package br.com.ecad.captacao.processingengine;

import java.util.List;

import br.com.ecad.captacao.shared.domain.entities.MetricaExecucaoIA;
import br.com.ecad.captacao.shared.infrastructure.repositories.MetricaIARepository;
import org.springframework.stereotype.Component;

/**
 * Step final: Salva métricas de execução IA.
 */
@Component
class MetricsStep implements PipelineStep {

    private final MetricaIARepository metricasIa;

    MetricsStep(MetricaIARepository metricasIa) {
        this.metricasIa = metricasIa;
    }

    @Override
    public void execute(PipelineContext ctx) {
        var lista = ctx.metricas;
        if (lista == null || lista.isEmpty()) return;
        for (var m : lista) {
            try {
                metricasIa.salvar(m);
            } catch (java.io.IOException e) {
                // Métricas são best-effort — falha de I/O não deve quebrar o pipeline
            }
        }
    }

    /** Define as métricas a serem salvas (chamado pelo pipeline após extração). */
    void setMetricas(List<MetricaExecucaoIA> metricas) {
        // Context mutation handled in pipeline orquestration
    }
}