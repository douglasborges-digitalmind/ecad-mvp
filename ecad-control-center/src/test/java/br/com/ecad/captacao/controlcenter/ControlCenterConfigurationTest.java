package br.com.ecad.captacao.controlcenter;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ControlCenterConfigurationTest {
    @Test
    void validateLancaExcecaoQuandoCloudSemMongoKafka() {
        var settings = new ControlCenterSettings(
            "", "", "", "", "", "",
            "scraping_commands", "captured_documents",
            "", "ecad-captacao");
        var localDev = new br.com.ecad.captacao.shared.infrastructure.local.LocalDevelopmentSettings(
            java.nio.file.Path.of("/tmp"), false);

        assertThrows(IllegalStateException.class, () -> settings.validate(localDev));
    }

    @Test
    void validateNaoLancaExcecaoQuandoCloudComMongoKafka() {
        var settings = new ControlCenterSettings(
            "kafka:9092", "", "", "", "", "",
            "scraping_commands", "captured_documents",
            "mongodb://localhost:27017", "ecad-captacao");
        var localDev = new br.com.ecad.captacao.shared.infrastructure.local.LocalDevelopmentSettings(
            java.nio.file.Path.of("/tmp"), false);

        assertDoesNotThrow(() -> settings.validate(localDev));
    }

    @Test
    void validateNaoLancaExcecaoQuandoLocalDevAtivo() {
        var settings = new ControlCenterSettings(
            "", "", "", "", "", "",
            "scraping_commands", "captured_documents",
            "", "ecad-captacao");
        var localDev = new br.com.ecad.captacao.shared.infrastructure.local.LocalDevelopmentSettings(
            java.nio.file.Path.of("/tmp"), true);

        assertDoesNotThrow(() -> settings.validate(localDev));
    }
}