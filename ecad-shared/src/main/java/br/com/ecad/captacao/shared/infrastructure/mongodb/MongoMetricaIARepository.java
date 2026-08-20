package br.com.ecad.captacao.shared.infrastructure.mongodb;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.bson.conversions.Bson;

import br.com.ecad.captacao.shared.domain.entities.MetricaExecucaoIA;
import br.com.ecad.captacao.shared.domain.enums.ComponenteIA;
import br.com.ecad.captacao.shared.domain.enums.TipoEvidencia;
import br.com.ecad.captacao.shared.infrastructure.repositories.MetricaIARepository;
import com.mongodb.client.MongoClient;
import com.mongodb.client.model.Filters;

public class MongoMetricaIARepository extends MongoRepositoryBase<MetricaExecucaoIA> implements MetricaIARepository {
    public MongoMetricaIARepository(MongoClient client, String databaseName) {
        super(client, databaseName, MongoCollectionNames.METRICAS_IA, MetricaExecucaoIA.class);
    }

    @Override
    protected String getPartitionKeyValue(MetricaExecucaoIA item) {
        return item.componente == null ? "" : item.componente.jsonValue();
    }

    @Override
    protected String getId(MetricaExecucaoIA item) {
        return item.id.toString();
    }

    @Override
    public void salvar(MetricaExecucaoIA metrica) {
        super.atualizarItem(metrica);
    }

    @Override
    public List<MetricaExecucaoIA> listar(OffsetDateTime inicio, OffsetDateTime fim, ComponenteIA componente,
        TipoEvidencia tipoDocumento, UUID idFonteCaptacao) {
        var clauses = new java.util.ArrayList<Bson>();
        if (inicio != null) clauses.add(Filters.gte("timestamp", inicio));
        if (fim != null) clauses.add(Filters.lte("timestamp", fim));
        if (componente != null) clauses.add(Filters.eq("componente", componente.jsonValue()));
        if (tipoDocumento != null) clauses.add(Filters.eq("tipo_documento", tipoDocumento.jsonValue()));
        if (idFonteCaptacao != null) clauses.add(Filters.eq("id_fonte_captacao", idFonteCaptacao.toString()));

        var partitionKey = componente == null ? null : componente.jsonValue();
        return executarQuery(clauses.isEmpty() ? Filters.empty() : Filters.and(clauses), partitionKey, true).stream()
            .sorted(Comparator.comparing((MetricaExecucaoIA item) -> item.timestamp, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
            .toList();
    }
}