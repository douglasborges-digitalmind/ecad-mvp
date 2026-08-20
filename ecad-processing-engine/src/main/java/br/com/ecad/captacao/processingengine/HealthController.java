package br.com.ecad.captacao.processingengine;

import java.nio.file.Files;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import br.com.ecad.captacao.shared.infrastructure.health.ServiceHealthResult;
import br.com.ecad.captacao.shared.infrastructure.local.LocalDevelopmentSettings;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class HealthController {
    private final ProcessingEngineSettings settings;
    private final LocalDevelopmentSettings localDevelopment;
    private final ProcessingCloudClients cloudClients;
    private final ProcessingEngineMonitor monitor;

    HealthController(
        ProcessingEngineSettings settings,
        LocalDevelopmentSettings localDevelopment,
        ProcessingCloudClients cloudClients,
        ProcessingEngineMonitor monitor) {
        this.settings = settings;
        this.localDevelopment = localDevelopment;
        this.cloudClients = cloudClients;
        this.monitor = monitor;
    }

    @GetMapping({"/health", "/api/health"})
    ResponseEntity<Map<String, Object>> health(HttpServletRequest request) {
        var services = new LinkedHashMap<String, Object>();
        var consumerRunning = monitor.isConsumerRunning();
        var healthy = consumerRunning;
        var providers = settings.getAiProviders();
        var configuredProviders = settings.getConfiguredAiProviders();
        var unavailableProviders = settings.getUnavailableAiProviders();
        var unsupportedProviders = settings.getUnsupportedAiProviders();
        var aiProviderHealthy = configuredProviders.length > 0 && unsupportedProviders.length == 0;
        var sgaHealth = evaluateSgaHealth();

        services.put("consumer", new ServiceHealthResult(consumerRunning, consumerRunning ? "running" : "stopped"));
        services.put("sga", sgaHealth);
        services.put("ai_provider_chain", Map.of(
            "healthy", aiProviderHealthy,
            "detail", aiProviderDetail(providers, configuredProviders, unavailableProviders, unsupportedProviders),
            "providers", providers,
            "configuredProviders", configuredProviders,
            "unavailableProviders", unavailableProviders,
            "unsupportedProviders", unsupportedProviders));
        healthy &= aiProviderHealthy && sgaHealth.healthy();

        if (localDevelopment.enabled) {
            var eventosPath = localDevelopment.dataRootPath().resolve("eventos.json");
            services.put("event_hubs", new ServiceHealthResult(true, "local_queue:" + settings.localConsumerRoute()));
            services.put("local_storage", new ServiceHealthResult(true, localDevelopment.storageRootPath().toString()));
            services.put("local_repository", new ServiceHealthResult(true, localDevelopment.dataRootPath().toString()));
            services.put("local_eventos_json", new ServiceHealthResult(Files.exists(eventosPath), eventosPath.toString()));
            services.put("local_processing_route", new ServiceHealthResult(true, settings.localConsumerRoute()));
            services.put("local_instance_id", new ServiceHealthResult(true, settings.instanceId()));
        } else {
            var mongoConfigured = !settings.mongoConnectionString().isBlank();
            var kafkaConfigured = !settings.kafkaBootstrapServers().isBlank();
            services.put("event_hubs", new ServiceHealthResult(
                kafkaConfigured && consumerRunning,
                kafkaConfigured ? "kafka:" + settings.capturedDocumentsTopic() : "configuration_incomplete"));
            services.put("persistence", new ServiceHealthResult(
                cloudClients.hasMongoClient(),
                cloudClients.hasMongoClient() ? "mongo:" + settings.mongoDatabaseName() : "configuration_incomplete"));
            services.put("blob_storage", new ServiceHealthResult(
                cloudClients.hasBlobStorage(),
                cloudClients.hasBlobStorage() ? "azure:" + settings.azureBlobContainerName() : "configuration_incomplete"));
            healthy &= mongoConfigured && cloudClients.hasBlobStorage() && kafkaConfigured;
        }

        var response = new LinkedHashMap<String, Object>();
        response.put("status", healthy ? "healthy" : "degraded");
        response.put("component", "ProcessingEngine");
        response.put("traceId", request.getRequestId());
        response.put("timestamp", OffsetDateTime.now());
        response.put("consumerRunning", consumerRunning);
        response.put("localDevelopment", localDevelopment.enabled);
        response.put("services", services);

        return ResponseEntity.status(healthy ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }

    private ServiceHealthResult evaluateSgaHealth() {
        if (!settings.sgaVerificationEnabled()) {
            return new ServiceHealthResult(true, "disabled");
        }

        var configured = !settings.sgaBaseUrl().isBlank()
            && (!settings.sgaAuthorization().isBlank()
                || (!settings.sgaOAuthUrl().isBlank() && !settings.sgaClientId().isBlank() && !settings.sgaClientSecret().isBlank()));
        return new ServiceHealthResult(configured, configured ? "configured" : "configuration_incomplete");
    }

    private static String aiProviderDetail(
        String[] providers,
        String[] configuredProviders,
        String[] unavailableProviders,
        String[] unsupportedProviders) {
        if (providers.length == 0) {
            return "not_configured";
        }
        if (unsupportedProviders.length > 0) {
            return "unsupported_providers: " + String.join(", ", unsupportedProviders);
        }
        if (configuredProviders.length == 0) {
            return "no_usable_provider";
        }
        return unavailableProviders.length == 0
            ? "configured"
            : "partial_configuration: " + String.join(", ", unavailableProviders);
    }
}
