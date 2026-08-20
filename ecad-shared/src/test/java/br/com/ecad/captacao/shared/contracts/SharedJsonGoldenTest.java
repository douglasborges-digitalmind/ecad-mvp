package br.com.ecad.captacao.shared.contracts;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import br.com.ecad.captacao.shared.JsonDefaults;
import br.com.ecad.captacao.shared.domain.entities.Evento;
import br.com.ecad.captacao.shared.domain.entities.FonteCaptacao;
import br.com.ecad.captacao.shared.domain.entities.MetricaExecucaoIA;
import br.com.ecad.captacao.shared.domain.entities.MetricaExecucaoOperacional;
import br.com.ecad.captacao.shared.domain.enums.CobrancaIngresso;
import br.com.ecad.captacao.shared.domain.enums.ProviderIA;
import br.com.ecad.captacao.shared.domain.enums.TipoEvidencia;
import org.junit.jupiter.api.Test;

class SharedJsonGoldenTest {
    private final com.fasterxml.jackson.databind.ObjectMapper mapper = JsonDefaults.objectMapper();

    @Test
    void executarScrapingShouldRoundTripGoldenJson() throws Exception {
        var command = mapper.readValue(sample("executar-scraping.json"), ExecutarScraping.class);

        assertThat(command.tipoAlvo()).isEqualTo(TipoEvidencia.CONTRATO_MUSICAL);
        assertThat(command.metadados()).containsEntry(KeysMetadados.MUNICIPIO, "Salvador");
        assertThat(mapper.readTree(mapper.writeValueAsString(command)).get("tipo_alvo").asText()).isEqualTo("contratoMusical");
    }

    @Test
    void documentoCapturadoShouldRoundTripGoldenJson() throws Exception {
        var document = mapper.readValue(sample("documento-capturado.json"), DocumentoCapturado.class);

        assertThat(document.tipo()).isEqualTo(TipoEvidencia.CONTRATO_MUSICAL);
        assertThat(document.metadados()).containsEntry(KeysMetadados.CONTENT_TYPE, "application/pdf");
        assertThat(mapper.readTree(mapper.writeValueAsString(document)).get("tipo").asText()).isEqualTo("contratoMusical");
    }

    @Test
    void domainSamplesShouldDeserializeWithCanonicalEnums() throws Exception {
        var fonte = mapper.readValue(sample("fonte-captacao.json"), FonteCaptacao.class);
        var evento = mapper.readValue(sample("evento.json"), Evento.class);
        var metricaIa = mapper.readValue(sample("metrica-execucao-ia.json"), MetricaExecucaoIA.class);
        var metricaOperacional = mapper.readValue(sample("metrica-execucao-operacional.json"), MetricaExecucaoOperacional.class);

        assertThat(fonte.canaisScraping).hasSize(1);
        assertThat(evento.cobrancaIngresso()).isEqualTo(CobrancaIngresso.SIM);
        assertThat(evento.evidencias().getFirst().urlOrigem()).isEqualTo("https://example.org/baseline/festival-local");
        assertThat(metricaIa.provider).isEqualTo(ProviderIA.GEMINI_NATIVO);
        assertThat(metricaOperacional.operacao).isEqualTo("processar_documento_capturado");
    }

    private static java.io.File sample(String fileName) {
        var root = Path.of(System.getProperty("maven.multiModuleProjectDirectory", ".."));
        var sample = root.resolve("docs").resolve("baseline").resolve("samples").resolve(fileName);
        assertThat(Files.exists(sample)).isTrue();
        return sample.toFile();
    }
}
