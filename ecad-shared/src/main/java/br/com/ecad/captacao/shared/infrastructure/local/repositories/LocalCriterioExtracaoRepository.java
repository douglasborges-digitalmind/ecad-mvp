package br.com.ecad.captacao.shared.infrastructure.local.repositories;

import java.io.IOException;
import java.util.Optional;

import br.com.ecad.captacao.shared.domain.entities.CriterioExtracao;
import br.com.ecad.captacao.shared.domain.enums.TipoDocumento;
import br.com.ecad.captacao.shared.infrastructure.local.LocalJsonFileStore;
import br.com.ecad.captacao.shared.infrastructure.repositories.CriterioExtracaoRepository;
import br.com.ecad.captacao.shared.referencedata.CriterioExtracaoSeedCatalog;

public class LocalCriterioExtracaoRepository implements CriterioExtracaoRepository {
    private static final String COLLECTION_NAME = "criterios-extracao";

    private final LocalJsonFileStore store;
    public LocalCriterioExtracaoRepository(LocalJsonFileStore store) {
        this.store = store;
    }

    @Override
    public Optional<CriterioExtracao> obterPorTipoDocumento(TipoDocumento tipoDocumento) throws IOException {
        ensureSeed();
        return store.readCollection(COLLECTION_NAME, CriterioExtracao.class).stream()
            .filter(item -> item.tipoDocumento == tipoDocumento)
            .findFirst();
    }

    @Override
    public CriterioExtracao criar(CriterioExtracao criterio) throws IOException {
        return store.mutateCollection(COLLECTION_NAME, CriterioExtracao.class, items -> {
            items.removeIf(item -> item.tipoDocumento == criterio.tipoDocumento);
            items.add(criterio);
            return criterio;
        });
    }

    @Override
    public CriterioExtracao atualizar(CriterioExtracao criterio) throws IOException {
        return criar(criterio);
    }

    private void ensureSeed() throws IOException {
        if (!store.readCollection(COLLECTION_NAME, CriterioExtracao.class).isEmpty()) {
            return;
        }

        store.writeCollection(COLLECTION_NAME, CriterioExtracaoSeedCatalog.create());
    }
}
