package br.com.ecad.captacao.shared.infrastructure.repositories;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import br.com.ecad.captacao.shared.domain.entities.Evento;
import br.com.ecad.captacao.shared.domain.enums.NivelCompletude;
import br.com.ecad.captacao.shared.domain.enums.StatusEvento;
import br.com.ecad.captacao.shared.domain.enums.StatusSGA;

public interface EventoRepository {
    Evento criar(Evento evento) throws IOException;

    /**
     * Obtem evento por id restrito a partition key (municipio).
     * Removido o overload {@code obterPorId(UUID)} cross-partition para forcar todos os
     * chamadores a especificar o municipio, evitando fan-out custoso no Cosmos DB.
     */
    Optional<Evento> obterPorId(UUID id, String municipio) throws IOException;

    Optional<Evento> buscarPorDedup(String titulo, String local, OffsetDateTime data, String municipio, String uf) throws IOException;

    Evento atualizar(Evento evento) throws IOException;

    List<Evento> listar(String municipio, StatusEvento status, StatusSGA statusSga, NivelCompletude nivelCompletude,
        OffsetDateTime dataInicio, OffsetDateTime dataTermino, String codigoEvento, String unidadeEcad) throws IOException;

    List<Evento> listarParaPlanilha() throws IOException;

    List<Evento> listarPorStatusSga(StatusSGA statusSga) throws IOException;
}
