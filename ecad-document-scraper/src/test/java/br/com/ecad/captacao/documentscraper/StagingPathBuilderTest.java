package br.com.ecad.captacao.documentscraper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import br.com.ecad.captacao.shared.contracts.ExecutarScraping;
import br.com.ecad.captacao.shared.domain.enums.TipoEvidencia;
import org.junit.jupiter.api.Test;

class StagingPathBuilderTest {
    @Test
    void preservaCaminhoInformadoNormalizandoBarras() {
        var comando = comando("/base\\saida/");

        assertThat(StagingPathBuilder.buildLocalStagingPath(comando)).isEqualTo("base/saida");
    }

    @Test
    void geraCaminhoPadraoComTipoEIdFonte() {
        var idFonte = UUID.randomUUID();
        var comando = new ExecutarScraping(
            "https://example.org",
            TipoEvidencia.CONTRATO_MUSICAL,
            "capturar",
            List.of(),
            null,
            idFonte,
            UUID.randomUUID(),
            Map.of());

        assertThat(StagingPathBuilder.buildLocalStagingPath(comando))
            .startsWith("staging-area/document-scraper/contratoMusical/")
            .endsWith(idFonte.toString());
    }

    private static ExecutarScraping comando(String stagingPath) {
        return new ExecutarScraping(
            "https://example.org",
            TipoEvidencia.CONTRATO_MUSICAL,
            "capturar",
            List.of(),
            stagingPath,
            UUID.randomUUID(),
            UUID.randomUUID(),
            Map.of());
    }
}
