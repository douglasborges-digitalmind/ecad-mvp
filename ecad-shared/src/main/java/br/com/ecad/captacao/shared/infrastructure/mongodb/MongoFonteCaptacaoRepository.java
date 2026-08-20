package br.com.ecad.captacao.shared.infrastructure.mongodb;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.bson.conversions.Bson;

import br.com.ecad.captacao.shared.domain.entities.FonteCaptacao;
import br.com.ecad.captacao.shared.infrastructure.repositories.FonteCaptacaoRepository;
import com.mongodb.client.MongoClient;
import com.mongodb.client.model.Filters;

public class MongoFonteCaptacaoRepository extends MongoRepositoryBase<FonteCaptacao> implements FonteCaptacaoRepository {
    public MongoFonteCaptacaoRepository(MongoClient client, String databaseName) {
        super(client, databaseName, MongoCollectionNames.FONTES_CAPTACAO, FonteCaptacao.class);
    }

    @Override
    protected String getPartitionKeyValue(FonteCaptacao item) {
        return item.unidadeEcad;
    }

    @Override
    protected String getId(FonteCaptacao item) {
        return item.id.toString();
    }

    @Override
    public FonteCaptacao criar(FonteCaptacao fonte) {
        return super.criarItem(fonte);
    }

    @Override
    public Optional<FonteCaptacao> obterPorId(UUID id) {
        return executarQuery(Filters.eq("id", id.toString())).stream().findFirst();
    }

    @Override
    public List<FonteCaptacao> listar(String unidadeEcad, Boolean ativo) {
        var clauses = new ArrayList<Bson>();
        if (Boolean.TRUE.equals(ativo)) {
            clauses.add(Filters.eq("canais_scraping.ativo", true));
        } else if (Boolean.FALSE.equals(ativo)) {
            clauses.add(Filters.eq("canais_scraping.ativo", false));
        }
        var filter = clauses.isEmpty() ? Filters.empty() : Filters.and(clauses);
        return executarQuery(filter, unidadeEcad, true);
    }

    @Override
    public FonteCaptacao atualizar(FonteCaptacao fonte) {
        return super.atualizarItem(fonte);
    }

    @Override
    public void remover(UUID id, String unidadeEcad) {
        super.removerItem(id.toString(), unidadeEcad);
    }

    @Override
    public List<FonteCaptacao> listarComScrapingsVencidos(OffsetDateTime dataReferencia) {
        return executarQuery(Filters.lte("canais_scraping.frequencia.proxima_execucao", dataReferencia));
    }
}