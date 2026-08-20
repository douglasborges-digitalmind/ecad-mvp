package br.com.ecad.captacao.controlcenter;

import java.net.URI;
import java.nio.file.Files;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

import br.com.ecad.captacao.shared.infrastructure.health.ServiceHealthResult;
import br.com.ecad.captacao.shared.infrastructure.local.LocalDevelopmentSettings;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.Status;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class HealthController {
    private final LocalDevelopmentSettings localDevelopmentSettings;
    private final ControlCenterSettings settings;
    private final Environment environment;
    private final HealthEndpoint healthEndpoint;

    HealthController(
        LocalDevelopmentSettings localDevelopmentSettings,
        ControlCenterSettings settings,
        Environment environment,
        HealthEndpoint healthEndpoint) {
        this.localDevelopmentSettings = localDevelopmentSettings;
        this.settings = settings;
        this.environment = environment;
        this.healthEndpoint = healthEndpoint;
    }

    @GetMapping({"/health", "/api/health"})
    ResponseEntity<Map<String, Object>> health(HttpServletRequest request) {
        var services = new LinkedHashMap<String, ServiceHealthResult>();

        if (localDevelopmentSettings.enabled) {
            services.put("local_storage", new ServiceHealthResult(true, localDevelopmentSettings.rootPath.toString()));
            services.put("local_queue", new ServiceHealthResult(true, localDevelopmentSettings.queueRootPath().toString()));
            services.put("local_repository", new ServiceHealthResult(true, localDevelopmentSettings.dataRootPath().toString()));
            var eventosPath = localDevelopmentSettings.dataRootPath().resolve("eventos.json");
            services.put("local_eventos_json", new ServiceHealthResult(Files.exists(eventosPath), eventosPath.toString()));
        } else {
            // Delega a verificação real para os HealthIndicators registrados no Spring Boot
            var health = healthEndpoint.health();
            var isHealthy = Status.UP.equals(health.getStatus());
            services.put("cosmos_db", new ServiceHealthResult(isHealthy, isHealthy ? "configured:" + settings.mongoDatabaseName() : "health_check_failed"));
            services.put("event_hubs", new ServiceHealthResult(isHealthy, isHealthy ? "configured" : "health_check_failed"));
            services.put("blob_storage", new ServiceHealthResult(isHealthy, isHealthy ? "configured" : "health_check_failed"));
            services.put("email_service", new ServiceHealthResult(true, "noop"));
        }

        services.put("sga", avaliarSga());

        var healthy = services.values().stream().allMatch(ServiceHealthResult::healthy);
        var body = new LinkedHashMap<String, Object>();
        body.put("status", healthy ? "healthy" : "degraded");
        body.put("component", "ControlCenter");
        body.put("environment", environment.getProperty("spring.profiles.active", "local"));
        body.put("traceId", request.getRequestId());
        body.put("correlationId", request.getAttribute(RequestCorrelationFilter.CORRELATION_ID_ATTRIBUTE));
        body.put("localDevelopment", localDevelopmentSettings.enabled);
        body.put("timestamp", OffsetDateTime.now(ZoneOffset.UTC));
        body.put("services", services);

        return ResponseEntity.status(healthy ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }

    private ServiceHealthResult avaliarSga() {
        var enabled = Boolean.parseBoolean(environment.getProperty("SGA_VERIFICATION_ENABLED", "true"));
        if (!enabled) {
            return new ServiceHealthResult(true, "disabled");
        }

        var baseUrl = environment.getProperty("SGA_BASE_URL", "https://backend.ecad.org.br/arrecadacao/api-show");
        if (baseUrl == null || baseUrl.isBlank()) {
            return new ServiceHealthResult(false, "base_url_not_configured");
        }

        try {
            var uri = URI.create(baseUrl);
            if (!uri.isAbsolute()) {
                return new ServiceHealthResult(false, "invalid_base_url");
            }
        } catch (IllegalArgumentException ex) {
            return new ServiceHealthResult(false, "invalid_base_url");
        }

        var authorization = environment.getProperty("SGA_AUTHORIZATION");
        var user = environment.getProperty("SGA_USER");
        var clientId = environment.getProperty("SGA_CLIENT_ID");
        var clientSecret = environment.getProperty("SGA_CLIENT_SECRET");

        var hasAuthorization = authorization != null && !authorization.isBlank();
        var hasOauth = clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank();

        var missing = new java.util.ArrayList<String>();
        if (!hasAuthorization && !hasOauth) {
            missing.add("authorization_or_oauth_credentials");
        }

        if (user == null || user.isBlank()) {
            missing.add("user");
        }

        return missing.isEmpty()
            ? new ServiceHealthResult(true, "configured")
            : new ServiceHealthResult(false, "missing_credentials: " + String.join(", ", missing));
    }
}
