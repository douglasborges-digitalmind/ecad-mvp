package br.com.ecad.captacao.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

class RuntimeReadinessTest {
    @Test
    void dockerComposeShouldDefineExpectedServicesAndSafeLocalDefaults() throws Exception {
        var root = workspaceRoot();
        var compose = Files.readString(root.resolve("docker-compose.yml"));

        for (var service : List.of("control-center", "processing-engine", "document-scraper", "sga-status-sync", "deduplicator", "log-analyser")) {
            assertThat(compose).contains("  " + service + ":");
        }

        assertThat(compose).contains("LOCAL_DEVELOPMENT_ROOT: /workspace/.localdev");
        assertThat(compose).contains("SGA_VERIFICATION_ENABLED: \"false\"");
        assertThat(compose).contains("--dry-run");
        assertThat(compose).contains("Analise-Telemetria-ECAD-docker.xlsx");
    }

    @Test
    void scraperDockerfilesShouldInstallPlaywrightChromium() throws Exception {
        var root = workspaceRoot();
        for (var dockerfile : List.of(
            root.resolve("ecad-document-scraper").resolve("Dockerfile"))) {
            var content = Files.readString(dockerfile);

            assertThat(content).contains("PLAYWRIGHT_BROWSERS_PATH=/ms-playwright");
            assertThat(content).contains("com.microsoft.playwright.CLI");
            assertThat(content).contains("install --with-deps chromium");
            assertThat(content).contains("libgtk-3-0");
        }
    }

    @Test
    void finalPhaseDocumentationShouldExist() {
        var docs = workspaceRoot().resolve("docs");

        assertThat(docs.resolve("ARQUITETURA.md")).exists();
        assertThat(docs.resolve("AZURE_DEPLOYMENT_Java.md")).exists();
        assertThat(docs.resolve("DOCKER_EXECUCAO_LOCAL.md")).exists();
        assertThat(docs.resolve("DOCUMENTACAO_GERAL.md")).exists();
    }

    @Test
    void cloudAgnosticDeploymentArtifactsShouldExistAndAvoidVersionedSecrets() throws Exception {
        var root = workspaceRoot();
        var deployScript = Files.readString(root.resolve("scripts").resolve("Azure-deploy").resolve("00-Deploy-AzureAll.ps1"));
        var runLocalScript = Files.readString(root.resolve("scripts").resolve("run-local.ps1"));
        var setupPncpScript = Files.readString(root.resolve("scripts").resolve("ecadexecute.ps1"));
        var envExample = Files.readString(root.resolve(".env.example"));

        // Cloud-agnostic scripts exist and reference correct services
        assertThat(deployScript).contains("control-center");
        assertThat(deployScript).contains("processing-engine");
        assertThat(deployScript).contains("NamePrefix");
        assertThat(runLocalScript).contains("docker compose");
        assertThat(runLocalScript).contains("mongodb");
        assertThat(runLocalScript).contains("kafka");
        assertThat(runLocalScript).contains("azurite");

        // .env.example contains cloud-agnostic config (no legacy Azure keys)
        assertThat(envExample).contains("MONGODB_CONNECTION_STRING");
        assertThat(envExample).contains("AZURE_STORAGE_CONNECTION_STRING");
        assertThat(envExample).contains("KAFKA_BOOTSTRAP_SERVERS");
        assertThat(envExample).doesNotContain("COSMOS_DB");
        assertThat(envExample).doesNotContain("EVENT_HUBS");
        assertThat(envExample).doesNotContain("S3_ENDPOINT_URL");
        assertThat(envExample).doesNotContain("S3_ACCESS_KEY");
        assertThat(envExample).doesNotContain("S3_SECRET_KEY");
        // Azurite connection string contains AccountKey= (emulator public key, acceptable)
        // but should not contain legacy SharedAccessKey= from Cosmos/Event Hubs
        assertThat(envExample).doesNotContain("SharedAccessKey=");
    }

    private static Path workspaceRoot() {
        return Path.of(System.getProperty("maven.multiModuleProjectDirectory", ".."));
    }
}
