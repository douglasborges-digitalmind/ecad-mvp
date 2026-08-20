package br.com.ecad.captacao.shared.infrastructure.local.repositories;

import java.io.IOException;

import br.com.ecad.captacao.shared.domain.entities.SequencialCodigoEvento;
import br.com.ecad.captacao.shared.infrastructure.local.LocalJsonFileStore;
import br.com.ecad.captacao.shared.infrastructure.repositories.SequencialRepository;

public class LocalSequencialRepository implements SequencialRepository {
    private static final String COLLECTION_NAME = "sequenciais";

    private final LocalJsonFileStore store;
    public LocalSequencialRepository(LocalJsonFileStore store) {
        this.store = store;
    }

    @Override
    public int proximoSequencial(int ano) throws IOException {
        return store.mutateCollection(COLLECTION_NAME, SequencialCodigoEvento.class, items -> {
            var item = items.stream().filter(existing -> existing.ano == ano).findFirst().orElse(null);
            if (item == null) {
                item = new SequencialCodigoEvento();
                item.id = SequencialCodigoEvento.gerarId(ano);
                item.ano = ano;
                item.ultimoSequencial = 0;
                items.add(item);
            }

            item.ultimoSequencial++;
            return item.ultimoSequencial;
        });
    }
}