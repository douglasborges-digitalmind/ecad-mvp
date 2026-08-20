package br.com.ecad.captacao.documentscraper;

import br.com.ecad.captacao.shared.domain.entities.Documento;
import br.com.ecad.captacao.shared.infrastructure.repositories.DocumentoRepository;

class NoOpDocumentoRepository implements DocumentoRepository {
    @Override
    public boolean urlJaFoiProcessada(String url) {
        return false;
    }

    @Override
    public boolean arquivoJaFoiProcessado(String hashConteudo) {
        return false;
    }

    @Override
    public void salvar(Documento documento) {
    }

    @Override
    public void atualizar(Documento documento) {
    }
}
