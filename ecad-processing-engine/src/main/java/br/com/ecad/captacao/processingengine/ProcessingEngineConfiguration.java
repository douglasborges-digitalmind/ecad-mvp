package br.com.ecad.captacao.processingengine;

import java.nio.file.Path;

import br.com.ecad.captacao.shared.infrastructure.blob.BlobStorage;
import br.com.ecad.captacao.shared.infrastructure.blob.DefaultBlobStorageService;
import br.com.ecad.captacao.shared.infrastructure.local.LocalBlobStorage;
import br.com.ecad.captacao.shared.infrastructure.local.LocalDevelopmentSettings;
import br.com.ecad.captacao.shared.infrastructure.local.LocalJsonFileStore;
import br.com.ecad.captacao.shared.infrastructure.local.LocalMessageQueue;
import br.com.ecad.captacao.shared.infrastructure.local.LocalServiceInstanceRegistry;
import br.com.ecad.captacao.shared.infrastructure.repositories.CriterioExtracaoRepository;
import br.com.ecad.captacao.shared.infrastructure.repositories.DocumentoRepository;
import br.com.ecad.captacao.shared.infrastructure.repositories.EventoRepository;
import br.com.ecad.captacao.shared.infrastructure.repositories.FonteCaptacaoRepository;
import br.com.ecad.captacao.shared.infrastructure.repositories.MetricaIARepository;
import br.com.ecad.captacao.shared.infrastructure.repositories.MetricaOperacionalRepository;
import br.com.ecad.captacao.shared.infrastructure.repositories.MunicipioUnidadeRepository;
import br.com.ecad.captacao.shared.infrastructure.repositories.RepositoryFactory;
import br.com.ecad.captacao.shared.infrastructure.repositories.SequencialRepository;
import br.com.ecad.captacao.shared.infrastructure.health.BlobHealthIndicator;
import br.com.ecad.captacao.shared.infrastructure.health.MongoHealthIndicator;
import br.com.ecad.captacao.shared.infrastructure.metrics.MetricsCollector;
import br.com.ecad.captacao.shared.infrastructure.sga.SgaConnectionSettings;
import br.com.ecad.captacao.shared.infrastructure.sga.SgaCredentialsProvider;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
class ProcessingEngineConfiguration {

    @Bean
    ProcessingEngineSettings processingEngineSettings(org.springframework.core.env.Environment environment) {
        return ProcessingEngineSettings.fromEnvironment(environment);
    }

    @Bean
    LocalDevelopmentSettings localDevelopmentSettings(
        ProcessingEngineSettings settings,
        @Value("${LOCAL_DEVELOPMENT_ENABLED:true}") boolean enabled,
        @Value("${LOCAL_DEVELOPMENT_ROOT:}") String configuredRoot) {
        var root = configuredRoot == null || configuredRoot.isBlank()
            ? Path.of(System.getProperty("java.io.tmpdir", "/tmp")).resolve("ecad-localdev")
            : Path.of(configuredRoot);
        var localSettings = new LocalDevelopmentSettings(root, enabled);
        localSettings.blobContainerName = settings.azureBlobContainerName();
        settings.validate(localSettings);
        return localSettings;
    }

    @Bean
    LocalJsonFileStore localJsonFileStore(LocalDevelopmentSettings settings) {
        return new LocalJsonFileStore(settings);
    }

    @Bean
    LocalMessageQueue localMessageQueue(LocalDevelopmentSettings settings) {
        return new LocalMessageQueue(settings);
    }

    @Bean
    BlobStorage localBlobStorage(LocalDevelopmentSettings settings) {
        return new LocalBlobStorage(settings);
    }

    @Bean
    DefaultBlobStorageService defaultBlobStorageService(
        BlobStorage localBlobStorage,
        ProcessingCloudClients cloudClients,
        LocalDevelopmentSettings localDevelopment,
        ProcessingEngineSettings settings) {
        return new DefaultBlobStorageService(
            cloudClients.blobStorage(),
            (LocalBlobStorage) localBlobStorage,
            localDevelopment,
            settings.blobStagingPrefix(),
            settings.blobProducaoPrefix());
    }

    @Bean
    LocalServiceInstanceRegistry localServiceInstanceRegistry(LocalDevelopmentSettings settings) {
        return new LocalServiceInstanceRegistry(settings);
    }

    @Bean(destroyMethod = "close")
    ProcessingCloudClients processingCloudClients(ProcessingEngineSettings settings, LocalDevelopmentSettings localDevelopment) {
        return new ProcessingCloudClients(settings, localDevelopment);
    }

    @Bean
    RepositoryFactory repositoryFactory(ProcessingCloudClients cloudClients, ProcessingEngineSettings settings, LocalJsonFileStore store) {
        return new RepositoryFactory(cloudClients.mongoClient(), settings.mongoDatabaseName(), store);
    }

    @Bean
    br.com.ecad.captacao.shared.infrastructure.quarantine.EventFailureTracker eventFailureTracker() {
        return new br.com.ecad.captacao.shared.infrastructure.quarantine.InMemoryEventFailureTracker();
    }

    @Bean DocumentoRepository documentoRepository(RepositoryFactory repos) { return repos.documentoRepository(); }
    @Bean EventoRepository eventoRepository(RepositoryFactory repos) { return repos.eventoRepository(); }
    @Bean FonteCaptacaoRepository fonteCaptacaoRepository(RepositoryFactory repos) { return repos.fonteCaptacaoRepository(); }
    @Bean CriterioExtracaoRepository criterioExtracaoRepository(RepositoryFactory repos) { return repos.criterioExtracaoRepository(); }
    @Bean MetricaIARepository metricaIARepository(RepositoryFactory repos) { return repos.metricaIARepository(); }
    @Bean MetricaOperacionalRepository metricaOperacionalRepository(RepositoryFactory repos) { return repos.metricaOperacionalRepository(); }
    @Bean MunicipioUnidadeRepository municipioUnidadeRepository(RepositoryFactory repos) { return repos.municipioUnidadeRepository(); }
    @Bean SequencialRepository sequencialRepository(RepositoryFactory repos) { return repos.sequencialRepository(); }

    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnExpression("'${LOCAL_DEVELOPMENT_ENABLED:true}'.equals('false')")
    MongoHealthIndicator mongoHealthIndicator(ProcessingCloudClients cloudClients, ProcessingEngineSettings settings) {
        return new MongoHealthIndicator(cloudClients.mongoClient(), settings.mongoDatabaseName());
    }

    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnExpression("'${LOCAL_DEVELOPMENT_ENABLED:true}'.equals('false')")
    BlobHealthIndicator blobHealthIndicator(ProcessingCloudClients cloudClients, ProcessingEngineSettings settings) {
        return new BlobHealthIndicator(cloudClients.blobStorage(), "azure:" + settings.azureBlobContainerName());
    }

    @Bean
    MetricsCollector metricsCollector(MeterRegistry meterRegistry) {
        return new MetricsCollector(meterRegistry, "processing-engine");
    }

    @Bean
    SgaCredentialsProvider sgaCredentialsProvider(ProcessingEngineSettings settings) {
        return new SgaCredentialsProvider(SgaConnectionSettings.of(
            settings.sgaOAuthUrl(),
            settings.sgaBaseUrl(),
            settings.sgaClientId(),
            settings.sgaClientSecret(),
            settings.sgaAuthorization(),
            settings.sgaUser()));
    }

    @Bean
    LocalQueueConsumerService localQueueConsumerService(
        ProcessingEngineSettings settings,
        LocalMessageQueue messageQueue,
        ProcessingPipeline pipeline) {
        return new LocalQueueConsumerService(settings, messageQueue, pipeline);
    }
}