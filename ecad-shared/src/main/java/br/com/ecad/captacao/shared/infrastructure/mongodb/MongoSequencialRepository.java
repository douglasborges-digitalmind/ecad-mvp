package br.com.ecad.captacao.shared.infrastructure.mongodb;

import br.com.ecad.captacao.shared.domain.entities.SequencialCodigoEvento;
import br.com.ecad.captacao.shared.infrastructure.repositories.SequencialRepository;
import com.mongodb.client.MongoClient;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;

public class MongoSequencialRepository extends MongoRepositoryBase<SequencialCodigoEvento> implements SequencialRepository {
    public MongoSequencialRepository(MongoClient client, String databaseName) {
        super(client, databaseName, MongoCollectionNames.SEQUENCIAIS, SequencialCodigoEvento.class);
    }

    @Override
    protected String getPartitionKeyValue(SequencialCodigoEvento item) {
        return Integer.toString(item.ano);
    }

    @Override
    protected String getId(SequencialCodigoEvento item) {
        return item.id;
    }

    @Override
    public synchronized int proximoSequencial(int ano) {
        var id = SequencialCodigoEvento.gerarId(ano);
        var filter = Filters.eq("_id", id);
        var existing = collection.find(filter).first();
        if (existing == null) {
            var seq = new SequencialCodigoEvento();
            seq.id = id;
            seq.ano = ano;
            seq.ultimoSequencial = 1;
            var doc = toDocument(seq);
            doc.put("_id", id);
            collection.insertOne(doc);
            return 1;
        }
        collection.updateOne(filter, Updates.inc("ultimo_sequencial", 1));
        var updated = collection.find(filter).first();
        return updated != null ? updated.getInteger("ultimo_sequencial", 0) : 1;
    }
}