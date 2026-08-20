package br.com.ecad.captacao.sgastatussync;

import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Duration;

import br.com.ecad.captacao.shared.infrastructure.mongodb.MongoEventoRepository;
import br.com.ecad.captacao.shared.infrastructure.local.LocalDevelopmentSettings;
import br.com.ecad.captacao.shared.infrastructure.local.LocalJsonFileStore;
import br.com.ecad.captacao.shared.infrastructure.local.repositories.LocalEventoRepository;
import br.com.ecad.captacao.shared.infrastructure.repositories.EventoRepository;
import br.com.ecad.captacao.shared.infrastructure.sga.SgaConnectionSettings;
import br.com.ecad.captacao.shared.infrastructure.sga.SgaCredentialsProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SgaStatusSyncSettings.class)
class SgaStatusSyncConfiguration {

    @Bean
    LocalDevelopmentSettings localDevelopmentSettings(
        SgaStatusSyncSettings settings,
        @Value("${LOCAL_DEVELOPMENT_ENABLED:true}") boolean enabled,
        @Value("${LOCAL_DEVELOPMENT_ROOT:}") String configuredRoot) {
        var root = configuredRoot == null || configuredRoot.isBlank()
            ? Path.of(System.getProperty("java.io.tmpdir", "/tmp")).resolve("ecad-localdev")
            : Path.of(configuredRoot);
        return new LocalDevelopmentSettings(root, enabled);
    }

    @Bean
    LocalJsonFileStore localJsonFileStore(LocalDevelopmentSettings settings) {
        return new LocalJsonFileStore(settings);
    }

    @Bean
    EventoRepository eventoRepository(LocalJsonFileStore store, LocalDevelopmentSettings localDevelopment, SgaStatusSyncSettings settings) {
        if (!localDevelopment.enabled && !settings.mongoConnectionString().isBlank()) {
            var mongoClient = br.com.ecad.captacao.shared.infrastructure.mongodb.MongoClientFactory.create(settings.mongoConnectionString());
            return new MongoEventoRepository(mongoClient, settings.mongoDatabaseName());
        }
        return new LocalEventoRepository(store);
    }

    @Bean
    HttpClient sgaHttpClient(SgaStatusSyncSettings settings) {
        return HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(settings.sgaTimeoutSeconds()))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    }

    /**
     * SgaCredentialsProvider compartilhado dentro do modulo sga-status-sync.
     */
    @Bean
    SgaCredentialsProvider sgaCredentialsProvider(SgaStatusSyncSettings settings) {
        return new SgaCredentialsProvider(SgaConnectionSettings.of(
            settings.sgaOAuthUrl(),
            settings.sgaBaseUrl(),
            settings.sgaClientId(),
            settings.sgaClientSecret(),
            settings.sgaAuthorization(),
            settings.sgaUser()));
    }
}
