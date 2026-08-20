package br.com.ecad.captacao.controlcenter;

import java.nio.file.Path;

import br.com.ecad.captacao.controlcenter.services.CloudEventPublisher;
import br.com.ecad.captacao.controlcenter.services.EventPublisher;
import br.com.ecad.captacao.controlcenter.services.LocalQueuePublisher;
import br.com.ecad.captacao.controlcenter.services.EmailService;
import br.com.ecad.captacao.controlcenter.services.NullEmailService;
import br.com.ecad.captacao.shared.infrastructure.local.LocalDevelopmentSettings;
import br.com.ecad.captacao.shared.infrastructure.local.LocalJsonFileStore;
import br.com.ecad.captacao.shared.infrastructure.local.LocalMessageQueue;
import br.com.ecad.captacao.shared.infrastructure.local.LocalServiceInstanceRegistry;
import br.com.ecad.captacao.shared.infrastructure.repositories.CriterioExtracaoRepository;
import br.com.ecad.captacao.shared.infrastructure.repositories.DestinatarioRepository;
import br.com.ecad.captacao.shared.infrastructure.repositories.EventoRepository;
import br.com.ecad.captacao.shared.infrastructure.repositories.FonteCaptacaoRepository;
import br.com.ecad.captacao.shared.infrastructure.repositories.MetricaIARepository;
import br.com.ecad.captacao.shared.infrastructure.repositories.MetricaOperacionalRepository;
import br.com.ecad.captacao.shared.infrastructure.repositories.RepositoryFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
class ControlCenterConfiguration {
    @Bean
    ControlCenterSettings controlCenterSettings(Environment environment) {
        return ControlCenterSettings.fromEnvironment(environment);
    }

    @Bean
    LocalDevelopmentSettings localDevelopmentSettings(
        ControlCenterSettings settings,
        @Value("${LOCAL_DEVELOPMENT_ENABLED:true}") boolean enabled,
        @Value("${LOCAL_DEVELOPMENT_ROOT:}") String configuredRoot) {
        var root = configuredRoot == null || configuredRoot.isBlank()
            ? Path.of(System.getProperty("java.io.tmpdir", "/tmp")).resolve("ecad-localdev")
            : Path.of(configuredRoot);
        var localSettings = new LocalDevelopmentSettings(root, enabled);
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
    LocalServiceInstanceRegistry localServiceInstanceRegistry(LocalDevelopmentSettings settings) {
        return new LocalServiceInstanceRegistry(settings);
    }

    @Bean(destroyMethod = "close")
    ControlCenterCloudClients controlCenterCloudClients(ControlCenterSettings settings, LocalDevelopmentSettings localDevelopment) {
        return new ControlCenterCloudClients(settings, localDevelopment);
    }

    @Bean
    RepositoryFactory repositoryFactory(ControlCenterCloudClients cloudClients, ControlCenterSettings settings, LocalJsonFileStore store) {
        return new RepositoryFactory(cloudClients.mongoClient(), settings.mongoDatabaseName(), store);
    }

    @Bean FonteCaptacaoRepository fonteCaptacaoRepository(RepositoryFactory repos) { return repos.fonteCaptacaoRepository(); }
    @Bean CriterioExtracaoRepository criterioExtracaoRepository(RepositoryFactory repos) { return repos.criterioExtracaoRepository(); }
    @Bean DestinatarioRepository destinatarioRepository(RepositoryFactory repos) { return repos.destinatarioRepository(); }
    @Bean EventoRepository eventoRepository(RepositoryFactory repos) { return repos.eventoRepository(); }
    @Bean MetricaIARepository metricaIARepository(RepositoryFactory repos) { return repos.metricaIARepository(); }
    @Bean MetricaOperacionalRepository metricaOperacionalRepository(RepositoryFactory repos) { return repos.metricaOperacionalRepository(); }

    @Bean
    EventPublisher eventPublisher(LocalMessageQueue queue, LocalServiceInstanceRegistry registry, ControlCenterCloudClients cloudClients, LocalDevelopmentSettings localDevelopmentSettings, ControlCenterSettings settings) {
        if (cloudClients.hasKafkaPublisher()) {
            return new CloudEventPublisher(
                cloudClients.scrapingCommandPublisher(),
                cloudClients.capturedDocumentPublisher(),
                settings.scrapingCommandsTopic(),
                settings.capturedDocumentsTopic());
        }
        return new LocalQueuePublisher(queue, registry, localDevelopmentSettings);
    }

    @Bean
    EmailService emailService() {
        return new NullEmailService();
    }
}