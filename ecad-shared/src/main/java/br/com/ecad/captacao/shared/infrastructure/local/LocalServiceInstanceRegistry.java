package br.com.ecad.captacao.shared.infrastructure.local;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;

import br.com.ecad.captacao.shared.JsonDefaults;
import br.com.ecad.captacao.shared.contracts.Routes;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

public class LocalServiceInstanceRegistry {
    private static final String PROCESSING_ENGINE_SERVICE_NAME = Routes.PROCESSING_ENGINE;

    private final LocalDevelopmentSettings settings;
    private final ObjectMapper mapper;

    public LocalServiceInstanceRegistry(LocalDevelopmentSettings settings) {
        this.settings = settings;
        this.mapper = JsonDefaults.objectMapper();
    }

    public void registerProcessingEngineHeartbeat(String instanceId, String route) throws IOException {
        registerHeartbeat(PROCESSING_ENGINE_SERVICE_NAME, instanceId, route);
    }

    public void removeProcessingEngineInstance(String instanceId) throws IOException {
        removeInstance(PROCESSING_ENGINE_SERVICE_NAME, instanceId);
    }

    public List<String> listActiveProcessingEngineRoutes(Duration staleAfter) throws IOException {
        return listActiveRoutes(PROCESSING_ENGINE_SERVICE_NAME, staleAfter);
    }

    public List<LocalServiceInstanceSnapshot> listProcessingEngineInstances(Duration staleAfter) throws IOException {
        return listInstances(PROCESSING_ENGINE_SERVICE_NAME, staleAfter);
    }

    public void registerHeartbeat(String serviceName, String instanceId, String route) throws IOException {
        var filePath = getInstanceFilePath(serviceName, instanceId);
        var directory = filePath.getParent();
        if (directory != null) {
            Files.createDirectories(directory);
        }

        var heartbeat = new LocalServiceInstanceHeartbeat(
            serviceName,
            instanceId,
            route,
            OffsetDateTime.now(ZoneOffset.UTC),
            machineName());

        LocalFileOperations.writeStringAtomically(filePath, mapper.writeValueAsString(heartbeat));
    }

    public void removeInstance(String serviceName, String instanceId) throws IOException {
        Files.deleteIfExists(getInstanceFilePath(serviceName, instanceId));
    }

    public List<String> listActiveRoutes(String serviceName, Duration staleAfter) throws IOException {
        return listInstances(serviceName, staleAfter).stream()
            .filter(LocalServiceInstanceSnapshot::active)
            .map(LocalServiceInstanceSnapshot::route)
            .distinct()
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .toList();
    }

    public List<LocalServiceInstanceSnapshot> listInstances(String serviceName, Duration staleAfter) throws IOException {
        var servicePath = settings.getServiceInstancesPath(serviceName);
        if (!Files.isDirectory(servicePath)) {
            return List.of();
        }

        var utcNow = OffsetDateTime.now(ZoneOffset.UTC);
        try (var paths = Files.list(servicePath)) {
            return paths
                .filter(path -> path.getFileName().toString().endsWith(".json"))
                .map(path -> readSnapshot(path, utcNow, staleAfter))
                .flatMap(List::stream)
                .sorted(Comparator.comparing(LocalServiceInstanceSnapshot::route, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(LocalServiceInstanceSnapshot::instanceId, String.CASE_INSENSITIVE_ORDER))
                .toList();
        }
    }

    private List<LocalServiceInstanceSnapshot> readSnapshot(Path path, OffsetDateTime utcNow, Duration staleAfter) {
        try {
            var json = Files.readString(path, StandardCharsets.UTF_8);
            var heartbeat = mapper.readValue(json, LocalServiceInstanceHeartbeat.class);
            if (heartbeat == null || heartbeat.route == null || heartbeat.route.isBlank()) {
                return List.of();
            }

            var active = Duration.between(heartbeat.lastSeenUtc, utcNow).compareTo(staleAfter) <= 0;
            if (!active) {
                tryDelete(path);
            }

            return List.of(new LocalServiceInstanceSnapshot(
                heartbeat.serviceName,
                heartbeat.instanceId,
                heartbeat.route,
                heartbeat.lastSeenUtc,
                heartbeat.machineName,
                active));
        } catch (IOException ex) {
            return List.of();
        }
    }

    private Path getInstanceFilePath(String serviceName, String instanceId) {
        return settings.getServiceInstancesPath(serviceName).resolve(instanceId + ".json");
    }

    private static void tryDelete(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }

    private static String machineName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (IOException ex) {
            return "unknown";
        }
    }

    private static final class LocalServiceInstanceHeartbeat {
        @JsonProperty("serviceName")
        public String serviceName;

        @JsonProperty("instanceId")
        public String instanceId;

        @JsonProperty("route")
        public String route;

        @JsonProperty("lastSeenUtc")
        public OffsetDateTime lastSeenUtc;

        @JsonProperty("machineName")
        public String machineName;

        @SuppressWarnings("unused")
        LocalServiceInstanceHeartbeat() {
        }

        LocalServiceInstanceHeartbeat(String serviceName, String instanceId, String route, OffsetDateTime lastSeenUtc, String machineName) {
            this.serviceName = serviceName;
            this.instanceId = instanceId;
            this.route = route;
            this.lastSeenUtc = lastSeenUtc;
            this.machineName = machineName;
        }
    }

    public record LocalServiceInstanceSnapshot(
        String serviceName,
        String instanceId,
        String route,
        OffsetDateTime lastSeenUtc,
        String machineName,
        boolean active) {
    }
}