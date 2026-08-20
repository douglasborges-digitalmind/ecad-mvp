package br.com.ecad.captacao.shared.infrastructure.repositories;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import br.com.ecad.captacao.shared.domain.entities.FonteCaptacao;

public interface FonteCaptacaoRepository {
    FonteCaptacao criar(FonteCaptacao fonte) throws IOException;

    Optional<FonteCaptacao> obterPorId(UUID id) throws IOException;

    List<FonteCaptacao> listar(String unidadeEcad, Boolean ativo) throws IOException;

    FonteCaptacao atualizar(FonteCaptacao fonte) throws IOException;

    void remover(UUID id, String unidadeEcad) throws IOException;

    List<FonteCaptacao> listarComScrapingsVencidos(OffsetDateTime dataReferencia) throws IOException;
}
