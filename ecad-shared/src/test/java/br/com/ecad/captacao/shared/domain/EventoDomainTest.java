package br.com.ecad.captacao.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.List;

import br.com.ecad.captacao.shared.domain.entities.Evento;
import br.com.ecad.captacao.shared.domain.entities.Evidencia;
import br.com.ecad.captacao.shared.domain.enums.NivelCompletude;
import br.com.ecad.captacao.shared.domain.enums.StatusEvento;
import org.junit.jupiter.api.Test;

class EventoDomainTest {
    @Test
    void calcularStatusShouldReturnRealizadoWhenEventFinished() {
        var evento = new Evento(
            null, null, null,
            OffsetDateTime.parse("2026-04-01T00:00:00Z"),
            OffsetDateTime.parse("2026-04-02T00:00:00Z"),
            null, null, null, null, null, null, null, null, null, null, null, null, null,
            StatusEvento.AGENDADO, null, null, null, null, null, null, null, null
        );

        var status = evento.calcularStatus(OffsetDateTime.parse("2026-04-03T12:00:00Z"));

        assertThat(status).isEqualTo(StatusEvento.REALIZADO);
    }

    @Test
    void calcularNivelCompletudeShouldMatchDotNetRules() {
        var ev = new Evidencia(0, null, "", "", null, "", null, null, null);
        var evento = new Evento(
            null, null, "Festival",
            OffsetDateTime.parse("2026-04-01T00:00:00Z"), null,
            "Praca Central", "Salvador", "BA", "BAHIA", null, null, null, null,
            List.of("Banda A"), null, null, null, null,
            null, null, null, null, null, null, null, null,
            List.of(ev, ev, ev)
        );

        var nivel = evento.calcularNivelCompletude();

        assertThat(nivel).isEqualTo(NivelCompletude.ALTO);
    }

    @Test
    void ehDuplicataDeShouldIgnoreCaseWhitespaceAndDiacritics() {
        var left = new Evento(
            null, null, "Show de Verao", OffsetDateTime.parse("2026-04-01T00:00:00Z"), null,
            "Praca Central", "Sao Paulo", "SP", null, null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null, null
        );
        var right = new Evento(
            null, null, " show de verão ", OffsetDateTime.parse("2026-04-01T20:00:00Z"), null,
            "PRACA   CENTRAL", "São Paulo", "sp", null, null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null, null
        );

        assertThat(left.ehDuplicataDe(right)).isTrue();
    }
}
