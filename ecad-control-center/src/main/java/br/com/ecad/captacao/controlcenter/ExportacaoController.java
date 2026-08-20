package br.com.ecad.captacao.controlcenter;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import br.com.ecad.captacao.controlcenter.models.MetricasCustosResponse;
import br.com.ecad.captacao.controlcenter.services.MetricasService;
import br.com.ecad.captacao.controlcenter.services.PlanilhaService;
import br.com.ecad.captacao.shared.JsonDefaults;
import br.com.ecad.captacao.shared.domain.entities.Evento;
import br.com.ecad.captacao.shared.domain.enums.ComponenteIA;
import br.com.ecad.captacao.shared.domain.enums.TipoEvidencia;
import br.com.ecad.captacao.shared.infrastructure.local.LocalDevelopmentSettings;
import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/exportacao")
class ExportacaoController {
    // Objects.requireNonNull força o Checker Framework a tratar o resultado de "new MediaType(...)" como @NonNull,
    // eliminando o alerta "Null type safety" do Java LSP sem alterar a semantica em runtime.
    private static final MediaType XLSX_MEDIA_TYPE = java.util.Objects.requireNonNull(
        new MediaType("application", "vnd.openxmlformats-officedocument.spreadsheetml.sheet"));

    private final PlanilhaService planilhaService;
    private final MetricasService metricasService;
    private final LocalDevelopmentSettings localDevelopmentSettings;

    ExportacaoController(PlanilhaService planilhaService, MetricasService metricasService, LocalDevelopmentSettings localDevelopmentSettings) {
        this.planilhaService = planilhaService;
        this.metricasService = metricasService;
        this.localDevelopmentSettings = localDevelopmentSettings;
    }

    @GetMapping("/planilha")
    ResponseEntity<byte[]> gerarPlanilha(@RequestParam(name = "localFile", required = false) String localFile) throws Exception {
        var bytes = !isBlank(localFile) ? gerarPlanilhaLocal(localFile) : planilhaService.gerarPlanilha();
        return xlsxResponse(bytes, "Planilha-Eventos-ECAD-" + LocalDate.now(ZoneOffset.UTC) + ".xlsx");
    }

    @PostMapping("/enviar")
    Map<String, String> gerarEEnviarPlanilha() throws Exception {
        var planilha = planilhaService.gerarPlanilha();
        planilhaService.enviarPorEmail(planilha);
        return Map.of("message", "Planilha gerada e enviada por e-mail com sucesso.");
    }

    @GetMapping("/relatorio-ia")
    Object exportarRelatorioIa(
        @RequestParam(name = "periodo_inicio", required = false) String periodoInicio,
        @RequestParam(name = "periodo_fim", required = false) String periodoFim,
        @RequestParam(name = "componente", required = false) String componente,
        @RequestParam(name = "tipo_documento", required = false) String tipoDocumento,
        @RequestParam(name = "id_fonte_captacao", required = false) UUID idFonteCaptacao,
        @RequestParam(name = "formato", required = false) String formato) throws Exception {
        var relatorio = metricasService.obterMetricasCustos(
            parseDate(periodoInicio),
            parseDate(periodoFim),
            isBlank(componente) ? null : ComponenteIA.fromJson(componente),
            isBlank(tipoDocumento) ? null : TipoEvidencia.fromJson(tipoDocumento),
            idFonteCaptacao);

        if (!"xlsx".equalsIgnoreCase(formato)) {
            return relatorio;
        }

        return xlsxResponse(gerarPlanilhaRelatorioIa(relatorio), "Relatorio-IA-ECAD-" + LocalDate.now(ZoneOffset.UTC) + ".xlsx");
    }

    private byte[] gerarPlanilhaLocal(String localFile) throws Exception {
        if (!localDevelopmentSettings.enabled) {
            throw new IllegalArgumentException("Local development is not enabled on this instance.");
        }

        var path = resolveLocalPath(localFile);
        if (!Files.exists(path)) {
            throw new java.io.FileNotFoundException("Local file not found: " + path);
        }

        var eventos = JsonDefaults.objectMapper().readValue(path.toFile(), new TypeReference<List<Evento>>() { });
        return planilhaService.gerarPlanilha(eventos);
    }

    private Path resolveLocalPath(String localFile) {
        var path = Path.of(localFile.trim());
        if (!path.isAbsolute()) {
            path = localDevelopmentSettings.rootPath.resolve(trimLeadingLocalDev(path.toString()));
        }

        path = path.toAbsolutePath().normalize();
        var localRoot = localDevelopmentSettings.rootPath.toAbsolutePath().normalize();
        if (!path.startsWith(localRoot) && !path.startsWith(Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize())) {
            throw new IllegalArgumentException("Local file path is outside the allowed roots: " + path);
        }

        return path;
    }

    private static String trimLeadingLocalDev(String path) {
        return path.replaceFirst("^\\.localdev[/\\\\]?", "");
    }

    private static byte[] gerarPlanilhaRelatorioIa(MetricasCustosResponse relatorio) throws Exception {
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var resumo = workbook.createSheet("Resumo IA");
            resumo.createRow(0).createCell(0).setCellValue("Metrica");
            resumo.getRow(0).createCell(1).setCellValue("Valor");
            write(resumo.createRow(1), "Custo Total USD", relatorio.custoTotal().totalUsd().toPlainString());
            write(resumo.createRow(2), "Custo Diario USD", relatorio.custoTotal().diarioUsd().toPlainString());
            write(resumo.createRow(3), "Custo Semanal USD", relatorio.custoTotal().semanalUsd().toPlainString());
            write(resumo.createRow(4), "Custo Mensal USD", relatorio.custoTotal().mensalUsd().toPlainString());
            write(resumo.createRow(5), "Taxa Sucesso %", relatorio.taxaSucessoPercentual().toPlainString());
            write(resumo.createRow(6), "Taxa Fallback %", relatorio.taxaFallbackPercentual().toPlainString());
            write(resumo.createRow(7), "Tokens Total", Long.toString(relatorio.tokens().total()));
            resumo.autoSizeColumn(0);
            resumo.autoSizeColumn(1);
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private static ResponseEntity<byte[]> xlsxResponse(byte[] bytes, String fileName) {
        return ResponseEntity.ok()
            .contentType(XLSX_MEDIA_TYPE)
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(fileName).build().toString())
            .body(bytes);
    }

    private static void write(org.apache.poi.ss.usermodel.Row row, String label, String value) {
        row.createCell(0).setCellValue(label);
        row.createCell(1).setCellValue(value);
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