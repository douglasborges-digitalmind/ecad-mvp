package br.com.ecad.captacao.shared.infrastructure.mongodb;

import java.util.List;
import java.util.Optional;

import org.bson.conversions.Bson;

import br.com.ecad.captacao.shared.domain.entities.CriterioExtracao;
import br.com.ecad.captacao.shared.domain.enums.TipoDocumento;
import br.com.ecad.captacao.shared.infrastructure.repositories.CriterioExtracaoRepository;
import br.com.ecad.captacao.shared.referencedata.CriterioExtracaoSeedCatalog;
import com.mongodb.client.MongoClient;
import com.mongodb.client.model.Filters;

public class MongoCriterioExtracaoRepository extends MongoRepositoryBase<CriterioExtracao> implements CriterioExtracaoRepository {
    public MongoCriterioExtracaoRepository(MongoClient client, String databaseName) {
        super(client, databaseName, MongoCollectionNames.CRITERIOS_EXTRACAO, CriterioExtracao.class);
        ensureSeed();
    }

    @Override
    protected String getPartitionKeyValue(CriterioExtracao item) {
        return item.tipoDocumento == null ? "" : item.tipoDocumento.jsonValue();
    }

    @Override
    protected String getId(CriterioExtracao item) {
        return item.id.toString();
    }

    @Override
    public Optional<CriterioExtracao> obterPorTipoDocumento(TipoDocumento tipoDocumento) {
        var filter = Filters.eq("tipoDocumento", tipoDocumento.jsonValue());
        return executarQuery(filter, tipoDocumento.jsonValue(), true).stream().findFirst();
    }

    @Override
    public CriterioExtracao criar(CriterioExtracao criterio) {
        return super.atualizarItem(criterio);
    }

    @Override
    public CriterioExtracao atualizar(CriterioExtracao criterio) {
        return super.atualizarItem(criterio);
    }

    private void ensureSeed() {
        for (var criterio : CriterioExtracaoSeedCatalog.create()) {
            super.atualizarItem(criterio);
        }
    }
}