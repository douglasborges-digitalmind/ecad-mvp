package br.com.ecad.captacao.processingengine;

import java.util.UUID;

import br.com.ecad.captacao.shared.domain.enums.TipoEvidencia;
class UnavailableAiProviderChain implements AiProviderChain {
    @Override
    public AiProviderExecution processar(
        String prompt,
        byte[] mediaBytes,
        String mimeType,
        TipoEvidencia tipoDocumento,
        UUID idFonteCaptacao) {
        throw new IllegalStateException("Cadeia de providers IA ainda nao configurada para execucao real no Java.");
    }
}
