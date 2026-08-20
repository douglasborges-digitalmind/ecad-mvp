package br.com.ecad.captacao.shared.infrastructure.mongodb;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.bson.conversions.Bson;

import br.com.ecad.captacao.shared.domain.entities.Documento;
import br.com.ecad.captacao.shared.infrastructure.repositories.DocumentoRepository;
import com.mongodb.client.MongoClient;
import com.mongodb.client.model.Filters;

public class MongoDocumentoRepository extends MongoRepositoryBase<Documento> implements DocumentoRepository {
    public MongoDocumentoRepository(MongoClient client, String databaseName) {
        super(client, databaseName, MongoCollectionNames.DOCUMENTOS, Documento.class);
    }

    @Override
    protected String getPartitionKeyValue(Documento item) {
        return item.id() == null ? "" : item.id().toString();
    }

    @Override
    protected String getId(Documento item) {
        return item.id() == null ? "" : item.id().toString();
    }

    @Override
    public boolean urlJaFoiProcessada(String url) {
        return executarScalarCount(Filters.eq("url", url)) > 0;
    }

    @Override
    public boolean arquivoJaFoiProcessado(String hashConteudo) {
        return executarScalarCount(Filters.eq("hash_conteudo", hashConteudo)) > 0;
    }

    @Override
    public void salvar(Documento documento) {
        super.criarItem(documento);
    }

    @Override
    public void atualizar(Documento documento) {
        super.atualizarItem(documento);
    }
}