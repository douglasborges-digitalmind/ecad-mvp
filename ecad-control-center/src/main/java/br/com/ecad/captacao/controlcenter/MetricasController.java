package br.com.ecad.captacao.controlcenter;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import br.com.ecad.captacao.controlcenter.models.MetricasCustosResponse;
import br.com.ecad.captacao.controlcenter.services.MetricasService;
import br.com.ecad.captacao.shared.domain.enums.ComponenteIA;
import br.com.ecad.captacao.shared.domain.enums.TipoEvidencia;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/metricas")
class MetricasController {
    private final MetricasService service;

    MetricasController(MetricasService service) {
        this.service = service;
    }

    @GetMapping("/custos")
    MetricasCustosResponse obterMetricasCustos(
        @RequestParam(name = "periodo_inicio", required = false) String periodoInicio,
        @RequestParam(name = "periodo_fim", required = false) String periodoFim,
        @RequestParam(name = "componente", required = false) String componente,
        @RequestParam(name = "tipo_documento", required = false) String tipoDocumento,
        @RequestParam(name = "id_fonte_captacao", required = false) UUID idFonteCaptacao) throws Exception {
        return service.obterMetricasCustos(
            parseDate(periodoInicio),
            parseDate(periodoFim),
            isBlank(componente) ? null : ComponenteIA.fromJson(componente),
            isBlank(tipoDocumento) ? null : TipoEvidencia.fromJson(tipoDocumento),
            idFonteCaptacao);
    }

    private static OffsetDateTime parseDate(String value) {
        if (isBlank(value)) {
            return null;
        }

        if (value.length() == 10) {
            return LocalDate.parse(value).atStartOfDay().atOffset(ZoneOffset.UTC);
        }

        return OffsetDateTime.parse(value);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
