package br.com.ecad.captacao.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class BaselineArtifactsTest {
    @Test
    void baselineSamplesShouldExist() {
        var root = Path.of(System.getProperty("maven.multiModuleProjectDirectory", ".."));
        var samples = root.resolve("docs").resolve("baseline").resolve("samples");

        assertThat(samples.resolve("executar-scraping.json")).exists();
        assertThat(samples.resolve("documento-capturado.json")).exists();
        assertThat(samples.resolve("fonte-captacao.json")).exists();
        assertThat(samples.resolve("evento.json")).exists();
        assertThat(samples.resolve("metrica-execucao-ia.json")).exists();
        assertThat(samples.resolve("metrica-execucao-operacional.json")).exists();
        assertThat(samples.resolve("planilha-baseline.xlsx")).exists();
    }
}