package br.com.ecad.captacao.shared.domain.valueobjects;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import br.com.ecad.captacao.shared.domain.enums.TipoFrequencia;
import com.fasterxml.jackson.annotation.JsonProperty;

public class FrequenciaScraping {
    private static final System.Logger LOGGER = System.getLogger(FrequenciaScraping.class.getName());

    @JsonProperty("tipo")
    public TipoFrequencia tipo = TipoFrequencia.DIARIO;

    @JsonProperty("dias_da_semana")
    public List<String> diasDaSemana = new ArrayList<>();

    @JsonProperty("horario")
    public String horario;

    @JsonProperty("proxima_execucao")
    public OffsetDateTime proximaExecucao;

    @JsonProperty("intervalo_horas")
    public Integer intervaloHoras;

    public OffsetDateTime calcularProximaExecucao(OffsetDateTime aPartirDe) {
        return switch (tipo) {
            case DIARIO -> calcularProximoDiario(aPartirDe);
            case SEMANAL -> calcularProximoSemanal(aPartirDe);
            case MENSAL -> aPartirDe.plusMonths(1).toLocalDate().atStartOfDay().atOffset(aPartirDe.getOffset());
            case PERSONALIZADO -> calcularProximoPersonalizado(aPartirDe);
        };
    }

    private OffsetDateTime calcularProximoDiario(OffsetDateTime aPartirDe) {
        var horarioLocal = parseHorario();
        if (horarioLocal == null) {
            return aPartirDe.plusDays(1).toLocalDate().atStartOfDay().atOffset(aPartirDe.getOffset());
        }

        var hojeNoHorario = aPartirDe.toLocalDate().atTime(horarioLocal).atOffset(aPartirDe.getOffset());
        return hojeNoHorario.isAfter(aPartirDe)
            ? hojeNoHorario
            : aPartirDe.plusDays(1).toLocalDate().atTime(horarioLocal).atOffset(aPartirDe.getOffset());
    }

    private OffsetDateTime calcularProximoSemanal(OffsetDateTime aPartirDe) {
        if (diasDaSemana == null || diasDaSemana.isEmpty()) {
            return aPartirDe.plusDays(7);
        }

        var diasMapeados = diasDaSemana.stream()
            .map(FrequenciaScraping::mapearDiaSemana)
            .filter(day -> day != null)
            .sorted(Comparator.naturalOrder())
            .toList();

        if (diasMapeados.isEmpty()) {
            return aPartirDe.plusDays(7);
        }

        var hoje = aPartirDe.getDayOfWeek();
        var horarioLocal = parseHorario();

        if (diasMapeados.contains(hoje)) {
            var hojeNoHorario = horarioLocal == null ? aPartirDe : aPartirDe.toLocalDate().atTime(horarioLocal).atOffset(aPartirDe.getOffset());
            if (hojeNoHorario.isAfter(aPartirDe)) {
                return hojeNoHorario;
            }
        }

        var proximoDia = diasMapeados.stream()
            .filter(day -> day.getValue() > hoje.getValue())
            .findFirst()
            .orElse(diasMapeados.getFirst());

        var diasParaAdicionar = (proximoDia.getValue() - hoje.getValue() + 7) % 7;
        if (diasParaAdicionar == 0) {
            diasParaAdicionar = 7;
        }

        var proximaData = aPartirDe.plusDays(diasParaAdicionar).toLocalDate();
        return horarioLocal == null
            ? proximaData.atStartOfDay().atOffset(aPartirDe.getOffset())
            : proximaData.atTime(horarioLocal).atOffset(aPartirDe.getOffset());
    }

    private OffsetDateTime calcularProximoPersonalizado(OffsetDateTime aPartirDe) {
        if (intervaloHoras != null && intervaloHoras > 0) {
            return aPartirDe.plusHours(intervaloHoras);
        }

        return aPartirDe.plusDays(1).toLocalDate().atStartOfDay().atOffset(aPartirDe.getOffset());
    }

    private LocalTime parseHorario() {
        try {
            return horario == null || horario.isBlank() ? null : LocalTime.parse(horario);
        } catch (RuntimeException ex) {
            LOGGER.log(System.Logger.Level.WARNING, "Horario de scraping invalido: " + horario, ex);
            return null;
        }
    }

    private static DayOfWeek mapearDiaSemana(String dia) {
        if (dia == null) {
            return null;
        }

        return switch (dia.toLowerCase()) {
            case "domingo" -> DayOfWeek.SUNDAY;
            case "segunda" -> DayOfWeek.MONDAY;
            case "terca", "terça" -> DayOfWeek.TUESDAY;
            case "quarta" -> DayOfWeek.WEDNESDAY;
            case "quinta" -> DayOfWeek.THURSDAY;
            case "sexta" -> DayOfWeek.FRIDAY;
            case "sabado", "sábado" -> DayOfWeek.SATURDAY;
            default -> null;
        };
    }
}
