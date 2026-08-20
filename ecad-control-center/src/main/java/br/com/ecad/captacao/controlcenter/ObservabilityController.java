package br.com.ecad.captacao.controlcenter;

import java.time.Duration;
import java.util.Map;

import br.com.ecad.captacao.shared.infrastructure.local.LocalDevelopmentSettings;
import br.com.ecad.captacao.shared.infrastructure.local.LocalServiceInstanceRegistry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/observability")
class ObservabilityController {
    private final LocalDevelopmentSettings localDevelopmentSettings;
    private final LocalServiceInstanceRegistry registry;

    ObservabilityController(LocalDevelopmentSettings localDevelopmentSettings, LocalServiceInstanceRegistry registry) {
        this.localDevelopmentSettings = localDevelopmentSettings;
        this.registry = registry;
    }

    @GetMapping("/processing-engines")
    Map<String, Object> obterProcessingEngines(@RequestParam(name = "stale_after_seconds", required = false) Integer staleAfterSeconds) throws Exception {
        var staleAfter = Math.max(1, staleAfterSeconds == null ? 15 : staleAfterSeconds);
        if (!localDevelopmentSettings.enabled) {
            return Map.of(
                "localDevelopment", false,
                "staleAfterSeconds", staleAfter,
                "activeRoutes", java.util.List.of(),
                "instances", java.util.List.of());
        }

        var instances = registry.listProcessingEngineInstances(Duration.ofSeconds(staleAfter));
        var activeRoutes = instances.stream()
            .filter(LocalServiceInstanceRegistry.LocalServiceInstanceSnapshot::active)
            .map(LocalServiceInstanceRegistry.LocalServiceInstanceSnapshot::route)
            .distinct()
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .toList();

        return Map.of(
            "localDevelopment", true,
            "staleAfterSeconds", staleAfter,
            "activeRoutes", activeRoutes,
            "instances", instances);
    }
}