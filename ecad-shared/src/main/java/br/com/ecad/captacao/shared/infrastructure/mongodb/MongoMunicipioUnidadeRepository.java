package br.com.ecad.captacao.shared.infrastructure.mongodb;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import br.com.ecad.captacao.shared.TextNormalization;
import br.com.ecad.captacao.shared.domain.entities.MunicipioUnidade;
import br.com.ecad.captacao.shared.infrastructure.repositories.MunicipioUnidadeRepository;
import com.mongodb.client.MongoClient;
import com.mongodb.client.model.Filters;

public class MongoMunicipioUnidadeRepository extends MongoRepositoryBase<MunicipioUnidade> implements MunicipioUnidadeRepository {
    public MongoMunicipioUnidadeRepository(MongoClient client, String databaseName) {
        super(client, databaseName, MongoCollectionNames.MUNICIPIOS_UNIDADE, MunicipioUnidade.class);
    }

    @Override
    protected String getPartitionKeyValue(MunicipioUnidade item) {
        return item.uf;
    }

    @Override
    protected String getId(MunicipioUnidade item) {
        return item.id.toString();
    }

    @Override
    public Optional<MunicipioUnidade> buscarPorUfMunicipio(String uf, String municipio) {
        return listarPorUf(uf).stream()
            .filter(item -> TextNormalization.equalsForComparison(item.municipio, municipio))
            .findFirst();
    }

    @Override
    public MunicipioUnidade criar(MunicipioUnidade municipioUnidade) {
        return super.criarItem(municipioUnidade);
    }

    @Override
    public List<MunicipioUnidade> listarPorUf(String uf) {
        return executarQuery(Filters.eq("uf", uf)).stream()
            .sorted(Comparator.comparing(item -> item.municipio, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
            .toList();
    }

    @Override
    public List<MunicipioUnidade> listarMunicipios() {
        return executarQuery(Filters.empty()).stream()
            .filter(item -> item.municipio != null && !item.municipio.isBlank()
                && item.uf != null && !item.uf.isBlank())
            .distinct()
            .sorted(Comparator
                .comparing((MunicipioUnidade item) -> item.municipio, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                .thenComparing(item -> item.uf, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
            .toList();
    }
}