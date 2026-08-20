package br.com.ecad.captacao.processingengine;

import br.com.ecad.captacao.shared.contracts.DocumentoCapturado;
import br.com.ecad.captacao.shared.domain.exceptions.ProcessingException;

interface ProcessingPipeline {
    void processar(DocumentoCapturado documento) throws ProcessingException;
}
