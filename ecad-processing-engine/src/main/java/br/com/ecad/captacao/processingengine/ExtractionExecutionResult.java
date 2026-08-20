package br.com.ecad.captacao.processingengine;

import java.util.List;

import br.com.ecad.captacao.shared.domain.entities.MetricaExecucaoIA;

record ExtractionExecutionResult(
    ExtractionResult resultado,
    List<MetricaExecucaoIA> metricas,
    ExtractionExecutionStatus status,
    String failureDetail) {
}
