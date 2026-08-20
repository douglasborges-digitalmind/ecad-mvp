package br.com.ecad.captacao.controlcenter.services;

import java.io.IOException;

import br.com.ecad.captacao.shared.contracts.DocumentoCapturado;
import br.com.ecad.captacao.shared.contracts.ExecutarScraping;

public interface EventPublisher {
    void publicarExecutarScraping(ExecutarScraping comando) throws IOException;

    void publicarDocumentoCapturado(DocumentoCapturado documento) throws IOException;
}
