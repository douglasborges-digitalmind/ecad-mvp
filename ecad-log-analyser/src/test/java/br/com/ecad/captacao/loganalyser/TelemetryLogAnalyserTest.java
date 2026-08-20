package br.com.ecad.captacao.loganalyser;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import br.com.ecad.captacao.shared.JsonDefaults;
import br.com.ecad.captacao.shared.domain.entities.MetricaExecucaoIA;
import br.com.ecad.captacao.shared.domain.entities.MetricaExecucaoOperacional;
import br.com.ecad.captacao.shared.domain.enums.ComponenteIA;
import br.com.ecad.captacao.shared.domain.enums.ProviderIA;
import br.com.ecad.captacao.shared.domain.enums.TipoEvidencia;
import br.com.ecad.captacao.shared.domain.enums.TipoOperacaoIA;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TelemetryLogAnalyserTest {
    @TempDir
    java.nio.file.Path tempDir;

    @Test
    void loadsTelemetryAndBuildsExpectedWorkbookSheets() throws Exception {
        var fonteId = UUID.randomUUID();
        var execucaoId = UUID.randomUUID();
        var dataDir = tempDir.resolve("data");
        Files.createDirectories(dataDir);
        Files.writeString(dataDir.resolve("metricas-ia.json"), JsonDefaults.objectMapper().writeValueAsString(List.of(metricIa(execucaoId, fonteId))));
        Files.writeString(dataDir.resolve("metricas-operacionais.json"), JsonDefaults.objectMapper().writeValueAsString(List.of(metricOperacional(execucaoId, fonteId))));
        Files.writeString(dataDir.resolve("fontes-captacao.json"), JsonDefaults.objectMapper().writeValueAsString(List.of(fonte(fonteId))));

        var options = AnalyzerOptions.parse(new String[] {
            "--input-dir", dataDir.toString(),
            "--output", tempDir.resolve("analysis.xlsx").toString()
        }, tempDir);
        var dataset = new TelemetryDataLoader().load(options);
        var bytes = new TelemetryWorkbookBuilder().build(dataset, options);

        assertThat(dataset.metricasIA()).hasSize(1);
        assertThat(dataset.metricasOperacionais()).hasSize(1);
        assertThat(dataset.fontes()).containsKey(fonteId);
        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(11);
            assertThat(workbook.getSheet("Resumo")).isNotNull();
            assertThat(workbook.getSheet("IA por Componente")).isNotNull();
            assertThat(workbook.getSheet("IA por Provider")).isNotNull();
            assertThat(workbook.getSheet("Operacional")).isNotNull();
            assertThat(workbook.getSheet("Fontes")).isNotNull();
            assertThat(workbook.getSheet("Detalhe IA")).isNotNull();
            assertThat(workbook.getSheet("Resumo").getRow(1).getCell(0).getStringCellValue()).isEqualTo("Diretorio analisado");
            assertThat(workbook.getSheet("Resumo").getRow(3).getCell(1).getStringCellValue()).isEqualTo("1");
        }
    }

    private static MetricaExecucaoIA metricIa(UUID execucaoId, UUID fonteId) {
        var metric = new MetricaExecucaoIA();
        metric.idExecucao = execucaoId;
        metric.componente = ComponenteIA.DOCUMENT_SCRAPER;
        metric.tipoOperacao = TipoOperacaoIA.DISCOVERY_LINKS;
        metric.tipoDocumento = TipoEvidencia.CONTRATO_MUSICAL;
        metric.provider = ProviderIA.OPEN_ROUTER;
        metric.modeloUtilizado = "test-model";
        metric.tokensInput = 100;
        metric.tokensOutput = 30;
        metric.custoUsd = new BigDecimal("0.0123");
        metric.duracaoChamadaMs = 250;
        metric.idFonteCaptacao = fonteId;
        metric.sucesso = true;
        metric.timestamp = OffsetDateTime.parse("2026-01-01T10:00:00Z");
        return metric;
    }

    private static MetricaExecucaoOperacional metricOperacional(UUID execucaoId, UUID fonteId) {
        var metric = new MetricaExecucaoOperacional();
        metric.idExecucao = execucaoId;
        metric.componente = ComponenteIA.PROCESSING_ENGINE;
        metric.operacao = "processarDocumento";
        metric.resultado = "sucesso";
        metric.sucesso = true;
        metric.itensProcessados = 4;
        metric.duracaoTotalMs = 500;
        metric.idFonteCaptacao = fonteId;
        metric.timestamp = OffsetDateTime.parse("2026-01-01T10:05:00Z");
        return metric;
    }

    private static FonteCaptacaoResumo fonte(UUID fonteId) {
        var fonte = new FonteCaptacaoResumo();
        fonte.id = fonteId;
        fonte.nome = "Portal Municipal";
        fonte.unidadeEcad = "SP";
        fonte.baseStoragePath = "SP/SaoPaulo";
        return fonte;
    }
}
