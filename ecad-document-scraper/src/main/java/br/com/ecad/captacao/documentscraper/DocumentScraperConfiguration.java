package br.com.ecad.captacao.documentscraper;

import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Duration;

import br.com.ecad.captacao.shared.infrastructure.blob.BlobStorage;
import br.com.ecad.captacao.shared.infrastructure.blob.DefaultBlobStorageService;
import br.com.ecad.captacao.shared.infrastructure.local.LocalBlobStorage;
import br.com.ecad.captacao.shared.infrastructure.local.LocalDevelopmentSettings;
import br.com.ecad.captacao.shared.infrastructure.local.LocalJsonFileStore;
import br.com.ecad.captacao.shared.infrastructure.local.LocalMessageQueue;
import br.com.ecad.captacao.shared.infrastructure.repositories.DocumentoRepository;
import br.com.ecad.captacao.shared.infrastructure.repositories.MetricaIARepository;
import br.com.ecad.captacao.shared.infrastructure.repositories.MetricaOperacionalRepository;
import br.com.ecad.captacao.shared.infrastructure.repositories.RepositoryFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
class DocumentScraperConfiguration {
    @Bean
    DocumentScraperSettings documentScraperSettings(Environment environment) {
        return DocumentScraperSettings.fromEnvironment(environment);
    }

    @Bean
    com.fasterxml.jackson.databind.ObjectMapper objectMapper() {
        return new com.fasterxml.jackson.databind.ObjectMapper()
            .findAndRegisterModules()
            .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Bean
    LocalDevelopmentSettings localDevelopmentSettings(
        DocumentScraperSettings settings,
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
    br.com.ecad.captacao.shared.infrastructure.blob.BlobStorage localBlobStorage(LocalDevelopmentSettings settings) {
        return new LocalBlobStorage(settings);
    }

    @Bean
    DefaultBlobStorageService defaultBlobStorageService(
        BlobStorage localBlobStorage,
        DocumentScraperCloudClients cloudClients,
        LocalDevelopmentSettings localDevelopment,
        DocumentScraperSettings settings) {
        return new DefaultBlobStorageService(
            cloudClients.blobStorage(),
            (LocalBlobStorage) localBlobStorage,
            localDevelopment,
            "staging/",
            "producao/");
    }

    @Bean(destroyMethod = "close")
    DocumentScraperCloudClients documentScraperCloudClients(DocumentScraperSettings settings, LocalDevelopmentSettings localDevelopment) {
        return new DocumentScraperCloudClients(settings, localDevelopment);
    }

    @Bean
    HttpClient scraperHttpClient() {
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).followRedirects(HttpClient.Redirect.NORMAL).build();
    }

    @Bean
    RepositoryFactory repositoryFactory(DocumentScraperCloudClients cloudClients, DocumentScraperSettings settings, LocalJsonFileStore store) {
        return new RepositoryFactory(cloudClients.mongoClient(), settings.mongoDatabaseName(), store);
    }

    @Bean DocumentoRepository documentoRepository(RepositoryFactory repos) { return repos.documentoRepository(); }
    @Bean MetricaIARepository metricaIARepository(RepositoryFactory repos) { return repos.metricaIARepository(); }
    @Bean MetricaOperacionalRepository metricaOperacionalRepository(RepositoryFactory repos) { return repos.metricaOperacionalRepository(); }
}