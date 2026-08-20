package br.com.ecad.captacao.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.List;

import br.com.ecad.captacao.shared.domain.enums.TipoFrequencia;
import br.com.ecad.captacao.shared.domain.valueobjects.FrequenciaScraping;
import org.junit.jupiter.api.Test;

class FrequenciaScrapingTest {
    @Test
    void calcularProximaExecucaoDiariaShouldUseSameDayWhenHorarioIsFuture() {
        var frequencia = new FrequenciaScraping();
        frequencia.tipo = TipoFrequencia.DIARIO;
        frequencia.horario = "18:30";

        var next = frequencia.calcularProximaExecucao(OffsetDateTime.parse("2026-04-01T10:00:00Z"));

        assertThat(next).isEqualTo(OffsetDateTime.parse("2026-04-01T18:30:00Z"));
    }

    @Test
    void calcularProximaExecucaoSemanalShouldMapPortugueseWeekDays() {
        var frequencia = new FrequenciaScraping();
        frequencia.tipo = TipoFrequencia.SEMANAL;
        frequencia.diasDaSemana = List.of("quarta", "sexta");
        frequencia.horario = "08:00";

        var next = frequencia.calcularProximaExecucao(OffsetDateTime.parse("2026-04-02T10:00:00Z"));

        assertThat(next).isEqualTo(OffsetDateTime.parse("2026-04-03T08:00:00Z"));
    }
}
