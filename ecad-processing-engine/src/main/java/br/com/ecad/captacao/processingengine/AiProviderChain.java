package br.com.ecad.captacao.processingengine;

import java.util.List;
import java.util.UUID;

import br.com.ecad.captacao.shared.domain.entities.MetricaExecucaoIA;
import br.com.ecad.captacao.shared.domain.enums.TipoEvidencia;

interface AiProviderChain {
    AiProviderExecution processar(
        String prompt,
        byte[] mediaBytes,
        String mimeType,
        TipoEvidencia tipoDocumento,
        UUID idFonteCaptacao) throws Exception;

    record AiProviderExecution(AiResponse response, List<MetricaExecucaoIA> metricas) {
    }
}
