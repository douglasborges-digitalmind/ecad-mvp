package br.com.ecad.captacao.shared.infrastructure.local.repositories;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import br.com.ecad.captacao.shared.domain.entities.Destinatario;
import br.com.ecad.captacao.shared.infrastructure.local.LocalJsonFileStore;
import br.com.ecad.captacao.shared.infrastructure.repositories.DestinatarioRepository;

public class LocalDestinatarioRepository implements DestinatarioRepository {
    private static final String COLLECTION_NAME = "destinatarios";

    private final LocalJsonFileStore store;
    public LocalDestinatarioRepository(LocalJsonFileStore store) {
        this.store = store;
    }

    @Override
    public Destinatario criar(Destinatario destinatario) throws IOException {
        return store.mutateCollection(COLLECTION_NAME, Destinatario.class, items -> {
            items.add(destinatario);
            return destinatario;
        });
    }

    @Override
    public List<Destinatario> listar() throws IOException {
        return store.readCollection(COLLECTION_NAME, Destinatario.class).stream()
            .sorted(Comparator.comparing(item -> item.nome, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
            .toList();
    }

    @Override
    public void remover(UUID id) throws IOException {
        store.mutateCollection(COLLECTION_NAME, Destinatario.class, items -> items.removeIf(item -> item.id.equals(id)));
    }
}