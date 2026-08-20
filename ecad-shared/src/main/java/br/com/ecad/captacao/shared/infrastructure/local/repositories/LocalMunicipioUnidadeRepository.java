package br.com.ecad.captacao.shared.infrastructure.local.repositories;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import br.com.ecad.captacao.shared.TextNormalization;
import br.com.ecad.captacao.shared.domain.entities.MunicipioUnidade;
import br.com.ecad.captacao.shared.infrastructure.local.LocalJsonFileStore;
import br.com.ecad.captacao.shared.infrastructure.repositories.MunicipioUnidadeRepository;
import br.com.ecad.captacao.shared.referencedata.MunicipioUnidadeReferenceCatalog;

public class LocalMunicipioUnidadeRepository implements MunicipioUnidadeRepository {
    private static final String COLLECTION_NAME = "municipios-unidades";

    private final LocalJsonFileStore store;
    public LocalMunicipioUnidadeRepository(LocalJsonFileStore store) {
        this.store = store;
    }

    @Override
    public Optional<MunicipioUnidade> buscarPorUfMunicipio(String uf, String municipio) throws IOException {
        ensureSeed();
        return store.readCollection(COLLECTION_NAME, MunicipioUnidade.class).stream()
            .filter(item -> TextNormalization.equalsForComparison(item.uf, uf))
            .filter(item -> TextNormalization.equalsForComparison(item.municipio, municipio))
            .findFirst();
    }

    @Override
    public MunicipioUnidade criar(MunicipioUnidade municipioUnidade) throws IOException {
        return store.mutateCollection(COLLECTION_NAME, MunicipioUnidade.class, items -> {
            items.removeIf(item -> TextNormalization.equalsForComparison(item.uf, municipioUnidade.uf)
                && TextNormalization.equalsForComparison(item.municipio, municipioUnidade.municipio));
            items.add(municipioUnidade);
            return municipioUnidade;
        });
    }

    @Override
    public List<MunicipioUnidade> listarPorUf(String uf) throws IOException {
        ensureSeed();
        return store.readCollection(COLLECTION_NAME, MunicipioUnidade.class).stream()
            .filter(item -> item.uf != null && item.uf.equalsIgnoreCase(uf))
            .sorted(Comparator.comparing(item -> item.municipio, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
            .toList();
    }

    @Override
    public List<MunicipioUnidade> listarMunicipios() throws IOException {
        ensureSeed();
        return store.readCollection(COLLECTION_NAME, MunicipioUnidade.class).stream()
            .filter(item -> item.municipio != null && !item.municipio.isBlank()
                && item.uf != null && !item.uf.isBlank())
            .distinct()
            .sorted(Comparator
                .comparing((MunicipioUnidade item) -> item.municipio, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                .thenComparing(item -> item.uf, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
            .toList();
    }

    private void ensureSeed() throws IOException {
        if (!store.readCollection(COLLECTION_NAME, MunicipioUnidade.class).isEmpty()) {
            return;
        }

        store.writeCollection(COLLECTION_NAME, MunicipioUnidadeReferenceCatalog.getAll());
    }
}
