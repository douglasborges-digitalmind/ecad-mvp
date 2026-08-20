package br.com.ecad.captacao.shared.infrastructure.repositories;

import java.io.IOException;
import java.util.Optional;

import br.com.ecad.captacao.shared.domain.entities.CriterioExtracao;
import br.com.ecad.captacao.shared.domain.enums.TipoDocumento;

public interface CriterioExtracaoRepository {
    Optional<CriterioExtracao> obterPorTipoDocumento(TipoDocumento tipoDocumento) throws IOException;

    CriterioExtracao criar(CriterioExtracao criterio) throws IOException;

    CriterioExtracao atualizar(CriterioExtracao criterio) throws IOException;
}
