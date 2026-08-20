package br.com.ecad.captacao.shared.infrastructure.local.repositories;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import br.com.ecad.captacao.shared.TextNormalization;
import br.com.ecad.captacao.shared.domain.entities.Evento;
import br.com.ecad.captacao.shared.domain.enums.NivelCompletude;
import br.com.ecad.captacao.shared.domain.enums.StatusEvento;
import br.com.ecad.captacao.shared.domain.enums.StatusSGA;
import br.com.ecad.captacao.shared.infrastructure.local.LocalJsonFileStore;
import br.com.ecad.captacao.shared.infrastructure.repositories.EventoRepository;

public class LocalEventoRepository implements EventoRepository {
    private static final String COLLECTION_NAME = "eventos";

    private final LocalJsonFileStore store;

    public LocalEventoRepository(LocalJsonFileStore store) {
        this.store = store;
    }

    @Override
    public Evento criar(Evento evento) throws IOException {
        return store.mutateCollection(COLLECTION_NAME, Evento.class, items -> {
            items.add(evento);
            return evento;
        });
    }

    @Override
    public Optional<Evento> obterPorId(UUID id, String municipio) throws IOException {
        return store.readCollection(COLLECTION_NAME, Evento.class).stream()
            .filter(item -> item.id().equals(id) && equalsIgnoreCase(item.municipio(), municipio))
            .findFirst();
    }

    @Override
    public Optional<Evento> buscarPorDedup(String titulo, String local, OffsetDateTime data, String municipio, String uf) throws IOException {
        return store.readCollection(COLLECTION_NAME, Evento.class).stream()
            .filter(item -> TextNormalization.equalsForComparison(item.titulo(), titulo))
            .filter(item -> TextNormalization.equalsForComparison(item.local(), local))
            .filter(item -> item.dataInicio() != null && data != null && item.dataInicio().toLocalDate().equals(data.toLocalDate()))
            .filter(item -> TextNormalization.equalsForComparison(item.municipio(), municipio))
            .filter(item -> TextNormalization.equalsForComparison(item.uf(), uf))
            .findFirst();
    }

    @Override
    public Evento atualizar(Evento evento) throws IOException {
        return store.mutateCollection(COLLECTION_NAME, Evento.class, items -> {
            for (var index = 0; index < items.size(); index++) {
                if (items.get(index).id().equals(evento.id())) {
                    items.set(index, evento);
                    return evento;
                }
            }

            throw new NoSuchElementException("Evento " + evento.id() + " nao encontrado.");
        });
    }

    @Override
    public List<Evento> listar(String municipio, StatusEvento status, StatusSGA statusSga, NivelCompletude nivelCompletude,
        OffsetDateTime dataInicio, OffsetDateTime dataTermino, String codigoEvento, String unidadeEcad) throws IOException {
        return store.readCollection(COLLECTION_NAME, Evento.class).stream()
            .filter(item -> isBlank(municipio) || equalsIgnoreCase(item.municipio(), municipio))
            .filter(item -> status == null || item.status() == status)
            .filter(item -> statusSga == null || item.statusSga() == statusSga)
            .filter(item -> nivelCompletude == null || item.nivelCompletude() == nivelCompletude)
            .filter(item -> dataInicio == null || item.dataInicio() != null && !item.dataInicio().toLocalDate().isBefore(dataInicio.toLocalDate()))
            .filter(item -> dataTermino == null || endOrStart(item) != null && !endOrStart(item).toLocalDate().isAfter(dataTermino.toLocalDate()))
            .filter(item -> isBlank(codigoEvento) || equalsIgnoreCase(item.codigoEvento(), codigoEvento))
            .filter(item -> isBlank(unidadeEcad) || equalsIgnoreCase(item.unidadeEcad(), unidadeEcad))
            .sorted(Comparator.comparing((Evento item) -> item.dataInicio(), Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(item -> item.titulo(), Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
            .toList();
    }

    @Override
    public List<Evento> listarParaPlanilha() throws IOException {
        return store.readCollection(COLLECTION_NAME, Evento.class).stream()
            .filter(item -> item.nivelCompletude() != NivelCompletude.INSUFICIENTE)
            .sorted(Comparator.comparing((Evento item) -> item.unidadeEcad(), Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                .thenComparing(item -> item.dataInicio(), Comparator.nullsLast(Comparator.naturalOrder())))
            .toList();
    }

    @Override
    public List<Evento> listarPorStatusSga(StatusSGA statusSga) throws IOException {
        return store.readCollection(COLLECTION_NAME, Evento.class).stream()
            .filter(item -> item.statusSga() == statusSga)
            .sorted(Comparator.comparing(item -> item.dataAtualizacao(), Comparator.nullsLast(Comparator.naturalOrder())))
            .toList();
    }

    private static OffsetDateTime endOrStart(Evento item) {
        return item.dataTermino() != null ? item.dataTermino() : item.dataInicio();
    }

    private static boolean equalsIgnoreCase(String left, String right) {
        return left != null && right != null && left.equalsIgnoreCase(right);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
