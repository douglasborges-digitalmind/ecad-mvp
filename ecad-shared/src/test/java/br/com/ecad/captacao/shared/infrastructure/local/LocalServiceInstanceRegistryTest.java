package br.com.ecad.captacao.shared.infrastructure.local;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalServiceInstanceRegistryTest {
    @TempDir
    Path tempDir;

    @Test
    void registerProcessingEngineHeartbeatShouldExposeActiveRoute() throws Exception {
        var settings = new LocalDevelopmentSettings(tempDir, true);
        var registry = new LocalServiceInstanceRegistry(settings);

        registry.registerProcessingEngineHeartbeat("processing-engine-2", "processing-engine-2");

        assertThat(registry.listActiveProcessingEngineRoutes(Duration.ofSeconds(15))).containsExactly("processing-engine-2");
        assertThat(registry.listProcessingEngineInstances(Duration.ofSeconds(15))).hasSize(1);
        try (var stream = Files.list(settings.getServiceInstancesPath("processing-engine"))) {
            assertThat(stream.map(path -> path.getFileName().toString()).toList())
                .containsExactly("processing-engine-2.json");
        }
    }
}
