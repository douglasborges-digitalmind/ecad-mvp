package br.com.ecad.captacao.sgastatussync;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

class SgaEventMatcherTest {
    private final SgaEventMatcher matcher = new SgaEventMatcher();

    @Test
    void acceptsMatchingTitleDateAndMunicipio() {
        var result = matcher.findBestValidMatch(
            new SgaEventQuery("Show Especial Ana Canta Brasil", LocalDate.of(2026, 5, 2), "Sao Paulo", "SP", 3550308),
            List.of(new SgaShowCandidate("Ana canta Brasil - show especial", LocalDate.of(2026, 5, 2), "S\u00e3o Paulo", "123", "ATIVO")));

        assertThat(result.found).isTrue();
        assertThat(result.titleThresholdUsed).isEqualTo(85);
        assertThat(result.municipioThresholdUsed).isEqualTo(75);
        assertThat(result.candidatePosition).isEqualTo(1);
    }

    @Test
    void elevatesThresholdsWhenMunicipioCodeIsMissing() {
        var result = matcher.findBestValidMatch(
            new SgaEventQuery("Festival de Verao", LocalDate.of(2026, 1, 8), "Niteroi", "RJ", null),
            List.of(new SgaShowCandidate("Festival de Verao", LocalDate.of(2026, 1, 8), "Niteroi", "777", "ATIVO")));

        assertThat(result.found).isTrue();
        assertThat(result.thresholdElevated).isTrue();
        assertThat(result.titleThresholdUsed).isEqualTo(92);
        assertThat(result.municipioThresholdUsed).isEqualTo(85);
    }
}
