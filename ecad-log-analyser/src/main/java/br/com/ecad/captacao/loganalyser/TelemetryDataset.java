package br.com.ecad.captacao.loganalyser;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import br.com.ecad.captacao.shared.domain.entities.MetricaExecucaoIA;
import br.com.ecad.captacao.shared.domain.entities.MetricaExecucaoOperacional;

record TelemetryDataset(List<MetricaExecucaoIA> metricasIA, List<MetricaExecucaoOperacional> metricasOperacionais, Map<UUID, FonteCaptacaoResumo> fontes) {
}
