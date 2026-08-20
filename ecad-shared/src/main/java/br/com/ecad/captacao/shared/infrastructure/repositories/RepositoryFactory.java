package br.com.ecad.captacao.shared.infrastructure.repositories;

import com.mongodb.client.MongoClient;

import br.com.ecad.captacao.shared.infrastructure.local.LocalJsonFileStore;
import br.com.ecad.captacao.shared.infrastructure.local.repositories.LocalDocumentoRepository;
import br.com.ecad.captacao.shared.infrastructure.local.repositories.LocalEventoRepository;
import br.com.ecad.captacao.shared.infrastructure.local.repositories.LocalFonteCaptacaoRepository;
import br.com.ecad.captacao.shared.infrastructure.local.repositories.LocalCriterioExtracaoRepository;
import br.com.ecad.captacao.shared.infrastructure.local.repositories.LocalMetricaIARepository;
import br.com.ecad.captacao.shared.infrastructure.local.repositories.LocalMetricaOperacionalRepository;
import br.com.ecad.captacao.shared.infrastructure.local.repositories.LocalMunicipioUnidadeRepository;
import br.com.ecad.captacao.shared.infrastructure.local.repositories.LocalSequencialRepository;
import br.com.ecad.captacao.shared.infrastructure.local.repositories.LocalDestinatarioRepository;
import br.com.ecad.captacao.shared.infrastructure.mongodb.MongoDocumentoRepository;
import br.com.ecad.captacao.shared.infrastructure.mongodb.MongoEventoRepository;
import br.com.ecad.captacao.shared.infrastructure.mongodb.MongoFonteCaptacaoRepository;
import br.com.ecad.captacao.shared.infrastructure.mongodb.MongoCriterioExtracaoRepository;
import br.com.ecad.captacao.shared.infrastructure.mongodb.MongoMetricaIARepository;
import br.com.ecad.captacao.shared.infrastructure.mongodb.MongoMetricaOperacionalRepository;
import br.com.ecad.captacao.shared.infrastructure.mongodb.MongoMunicipioUnidadeRepository;
import br.com.ecad.captacao.shared.infrastructure.mongodb.MongoSequencialRepository;
import br.com.ecad.captacao.shared.infrastructure.mongodb.MongoDestinatarioRepository;

/**
 * Factory centralizada de repositórios que seleciona automaticamente a implementação
 * Mongo (cloud) ou Local (filesystem) baseado na disponibilidade do MongoClient.
 *
 * <p>Elimina a duplicação do padrão if-hasMongoClient-else-local presente em
 * ProcessingEngineConfiguration, DocumentScraperConfiguration e ControlCenterConfiguration.</p>
 */
public final class RepositoryFactory {

    private final MongoClient mongoClient;
    private final String mongoDatabaseName;
    private final LocalJsonFileStore store;

    public RepositoryFactory(MongoClient mongoClient, String mongoDatabaseName, LocalJsonFileStore store) {
        this.mongoClient = mongoClient;
        this.mongoDatabaseName = mongoDatabaseName;
        this.store = store;
    }

    private boolean hasMongo() {
        return mongoClient != null && mongoDatabaseName != null && !mongoDatabaseName.isBlank();
    }

    public DocumentoRepository documentoRepository() {
        return hasMongo()
            ? new MongoDocumentoRepository(mongoClient, mongoDatabaseName)
            : new LocalDocumentoRepository(store);
    }

    public EventoRepository eventoRepository() {
        return hasMongo()
            ? new MongoEventoRepository(mongoClient, mongoDatabaseName)
            : new LocalEventoRepository(store);
    }

    public FonteCaptacaoRepository fonteCaptacaoRepository() {
        return hasMongo()
            ? new MongoFonteCaptacaoRepository(mongoClient, mongoDatabaseName)
            : new LocalFonteCaptacaoRepository(store);
    }

    public CriterioExtracaoRepository criterioExtracaoRepository() {
        return hasMongo()
            ? new MongoCriterioExtracaoRepository(mongoClient, mongoDatabaseName)
            : new LocalCriterioExtracaoRepository(store);
    }

    public MetricaIARepository metricaIARepository() {
        return hasMongo()
            ? new MongoMetricaIARepository(mongoClient, mongoDatabaseName)
            : new LocalMetricaIARepository(store);
    }

    public MetricaOperacionalRepository metricaOperacionalRepository() {
        return hasMongo()
            ? new MongoMetricaOperacionalRepository(mongoClient, mongoDatabaseName)
            : new LocalMetricaOperacionalRepository(store);
    }

    public MunicipioUnidadeRepository municipioUnidadeRepository() {
        return hasMongo()
            ? new MongoMunicipioUnidadeRepository(mongoClient, mongoDatabaseName)
            : new LocalMunicipioUnidadeRepository(store);
    }

    public SequencialRepository sequencialRepository() {
        return hasMongo()
            ? new MongoSequencialRepository(mongoClient, mongoDatabaseName)
            : new LocalSequencialRepository(store);
    }

    public DestinatarioRepository destinatarioRepository() {
        return hasMongo()
            ? new MongoDestinatarioRepository(mongoClient, mongoDatabaseName)
            : new LocalDestinatarioRepository(store);
    }
}