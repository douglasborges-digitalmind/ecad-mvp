package br.com.ecad.captacao.shared.infrastructure.repositories;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import br.com.ecad.captacao.shared.domain.entities.Destinatario;

public interface DestinatarioRepository {
    Destinatario criar(Destinatario destinatario) throws IOException;

    List<Destinatario> listar() throws IOException;

    void remover(UUID id) throws IOException;
}
