package br.com.ecad.captacao.documentscraper;

import br.com.ecad.captacao.shared.contracts.DocumentoCapturado;

interface CapturedDocumentPublisher {
    void publicar(DocumentoCapturado documento) throws Exception;
}
