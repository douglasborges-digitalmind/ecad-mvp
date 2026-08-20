package br.com.ecad.captacao.shared.infrastructure.mongodb;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import br.com.ecad.captacao.shared.domain.entities.Destinatario;
import br.com.ecad.captacao.shared.infrastructure.repositories.DestinatarioRepository;
import com.mongodb.client.MongoClient;

public class MongoDestinatarioRepository extends MongoRepositoryBase<Destinatario> implements DestinatarioRepository {
    public MongoDestinatarioRepository(MongoClient client, String databaseName) {
        super(client, databaseName, MongoCollectionNames.DESTINATARIOS, Destinatario.class);
    }

    @Override
    protected String getPartitionKeyValue(Destinatario item) {
        return item.id.toString();
    }

    @Override
    protected String getId(Destinatario item) {
        return item.id.toString();
    }

    @Override
    public Destinatario criar(Destinatario destinatario) {
        return super.criarItem(destinatario);
    }

    @Override
    public List<Destinatario> listar() {
        return executarQuery(new org.bson.Document()).stream()
            .sorted(Comparator.comparing(item -> item.nome, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
            .toList();
    }

    @Override
    public void remover(UUID id) {
        super.removerItem(id.toString(), id.toString());
    }
}