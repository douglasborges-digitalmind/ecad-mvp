package br.com.ecad.captacao.loganalyser;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import br.com.ecad.captacao.shared.domain.entities.MetricaExecucaoIA;
import br.com.ecad.captacao.shared.domain.entities.MetricaExecucaoOperacional;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

class TelemetryWorkbookBuilder {
    byte[] build(TelemetryDataset dataset, AnalyzerOptions options) throws Exception {
        try (var workbook = new XSSFWorkbook()) {
            buildResumoSheet(workbook, dataset, options);
            buildInsightsSheet(workbook, dataset);
            buildIaComponentSheet(workbook, dataset);
            buildIaProviderSheet(workbook, dataset);
            buildOperationalSheet(workbook, dataset);
            buildFontesSheet(workbook, dataset);
            buildTopFontesSheet(workbook, dataset);
            buildFallbacksSheet(workbook, dataset);
            buildAnomaliasSheet(workbook, dataset);
            buildIaDetailSheet(workbook, dataset);
            buildOperationalDetailSheet(workbook, dataset);
            try (var output = new ByteArrayOutputStream()) {
                workbook.write(output);
                return output.toByteArray();
            }
        }
    }

    private static void buildResumoSheet(Workbook workbook, TelemetryDataset dataset, AnalyzerOptions options) {
        var ia = dataset.metricasIA();
        var operacional = dataset.metricasOperacionais();
        var execucoesComFallback = ia.stream().collect(Collectors.groupingBy(metric -> metric.idExecucao)).values().stream()
            .filter(items -> items.stream().map(metric -> metric.provider).distinct().count() > 1)
            .count();
        var rows = new ArrayList<Map<String, Object>>();
        rows.add(row("Indicador", "Diretorio analisado", "Valor", options.inputDirectory()));
        rows.add(row("Indicador", "Fontes conhecidas", "Valor", dataset.fontes().size()));
        rows.add(row("Indicador", "Chamadas de IA", "Valor", ia.size()));
        rows.add(row("Indicador", "Execucoes operacionais", "Valor", operacional.size()));
        rows.add(row("Indicador", "Falhas IA", "Valor", ia.stream().filter(metric -> !metric.sucesso).count()));
        rows.add(row("Indicador", "Falhas operacionais", "Valor", operacional.stream().filter(metric -> !metric.sucesso).count()));
        rows.add(row("Indicador", "Execucoes com fallback IA", "Valor", execucoesComFallback));
        rows.add(row("Indicador", "Custo total USD", "Valor", sumCost(ia)));
        rows.add(row("Indicador", "Tokens de entrada", "Valor", ia.stream().mapToInt(metric -> metric.tokensInput).sum()));
        rows.add(row("Indicador", "Tokens de saida", "Valor", ia.stream().mapToInt(metric -> metric.tokensOutput).sum()));
        rows.add(row("Indicador", "Latencia media IA (ms)", "Valor", average(ia.stream().map(metric -> (double) metric.duracaoChamadaMs).toList())));
        rows.add(row("Indicador", "Latencia p95 IA (ms)", "Valor", percentile(ia.stream().map(metric -> (double) metric.duracaoChamadaMs).toList(), 95)));
        rows.add(row("Indicador", "Taxa sucesso IA (%)", "Valor", percent((int) ia.stream().filter(metric -> metric.sucesso).count(), ia.size())));
        rows.add(row("Indicador", "Taxa sucesso operacional (%)", "Valor", percent((int) operacional.stream().filter(metric -> metric.sucesso).count(), operacional.size())));
        rows.add(row("Indicador", "Itens processados", "Valor", operacional.stream().mapToInt(metric -> metric.itensProcessados).sum()));
        writeTabular(workbook, "Resumo", rows);
    }

    private static void buildInsightsSheet(Workbook workbook, TelemetryDataset dataset) {
        var ia = dataset.metricasIA();
        var operacional = dataset.metricasOperacionais();
        var custoPorFonte = ia.stream().collect(Collectors.groupingBy(metric -> metric.idFonteCaptacao)).entrySet().stream()
            .map(entry -> row("Indicador", "Fonte com maior custo IA", "Valor", resolveFonteNome(dataset, entry.getKey()) + " (" + sumCost(entry.getValue()) + " USD)"))
            .findFirst().orElse(row("Indicador", "Fonte com maior custo IA", "Valor", ""));
        var fonteComFalha = operacional.stream().filter(metric -> metric.idFonteCaptacao != null && !metric.sucesso)
            .collect(Collectors.groupingBy(metric -> metric.idFonteCaptacao)).entrySet().stream()
            .max(Comparator.comparingInt(entry -> entry.getValue().size()))
            .map(entry -> resolveFonteNome(dataset, entry.getKey()) + " (" + entry.getValue().size() + " falhas)")
            .orElse("");
        var rows = List.of(
            custoPorFonte,
            row("Indicador", "Componente com maior latencia media IA", "Valor", topAverageIaComponent(ia)),
            row("Indicador", "Fonte com mais falhas operacionais", "Valor", fonteComFalha),
            row("Indicador", "Execucoes operacionais com zero itens", "Valor", operacional.stream().filter(metric -> metric.itensProcessados == 0).count()),
            row("Indicador", "Chamadas IA com custo zero/nulo", "Valor", ia.stream().filter(metric -> metric.custoUsd == null || BigDecimal.ZERO.compareTo(metric.custoUsd) == 0).count()));
        writeTabular(workbook, "Insights", rows);
    }

    private static void buildIaComponentSheet(Workbook workbook, TelemetryDataset dataset) {
        var rows = dataset.metricasIA().stream().collect(Collectors.groupingBy(metric -> metric.componente)).entrySet().stream()
            .map(entry -> row(
                "Componente", text(entry.getKey()),
                "Chamadas", entry.getValue().size(),
                "SucessoPct", percent((int) entry.getValue().stream().filter(metric -> metric.sucesso).count(), entry.getValue().size()),
                "CustoUsd", sumCost(entry.getValue()),
                "TokensInput", entry.getValue().stream().mapToInt(metric -> metric.tokensInput).sum(),
                "TokensOutput", entry.getValue().stream().mapToInt(metric -> metric.tokensOutput).sum(),
                "LatenciaMediaMs", average(entry.getValue().stream().map(metric -> (double) metric.duracaoChamadaMs).toList()),
                "LatenciaMaxMs", entry.getValue().stream().mapToLong(metric -> metric.duracaoChamadaMs).max().orElse(0)))
            .toList();
        writeTabular(workbook, "IA por Componente", rows);
    }

    private static void buildIaProviderSheet(Workbook workbook, TelemetryDataset dataset) {
        var rows = dataset.metricasIA().stream().collect(Collectors.groupingBy(metric -> text(metric.provider) + "|" + safe(metric.modeloUtilizado))).values().stream()
            .map(items -> row(
                "Provider", text(items.getFirst().provider),
                "Modelo", safe(items.getFirst().modeloUtilizado),
                "Chamadas", items.size(),
                "SucessoPct", percent((int) items.stream().filter(metric -> metric.sucesso).count(), items.size()),
                "CustoUsd", sumCost(items),
                "TokensTotais", items.stream().mapToInt(metric -> metric.tokensInput + metric.tokensOutput).sum(),
                "LatenciaMediaMs", average(items.stream().map(metric -> (double) metric.duracaoChamadaMs).toList())))
            .toList();
        writeTabular(workbook, "IA por Provider", rows);
    }

    private static void buildOperationalSheet(Workbook workbook, TelemetryDataset dataset) {
        var rows = dataset.metricasOperacionais().stream().collect(Collectors.groupingBy(metric -> text(metric.componente) + "|" + safe(metric.operacao) + "|" + safe(metric.resultado))).values().stream()
            .map(items -> row(
                "Componente", text(items.getFirst().componente),
                "Operacao", safe(items.getFirst().operacao),
                "Resultado", safe(items.getFirst().resultado),
                "Execucoes", items.size(),
                "SucessoPct", percent((int) items.stream().filter(metric -> metric.sucesso).count(), items.size()),
                "ItensProcessados", items.stream().mapToInt(metric -> metric.itensProcessados).sum(),
                "DuracaoMediaMs", average(items.stream().map(metric -> (double) metric.duracaoTotalMs).toList()),
                "DuracaoMaxMs", items.stream().mapToLong(metric -> metric.duracaoTotalMs).max().orElse(0)))
            .toList();
        writeTabular(workbook, "Operacional", rows);
    }

    private static void buildFontesSheet(Workbook workbook, TelemetryDataset dataset) {
        var ids = fonteIds(dataset);
        var rows = ids.stream().map(id -> {
            var ia = dataset.metricasIA().stream().filter(metric -> id.equals(metric.idFonteCaptacao)).toList();
            var op = dataset.metricasOperacionais().stream().filter(metric -> id.equals(metric.idFonteCaptacao)).toList();
            var fonte = dataset.fontes().get(id);
            return row(
                "FonteId", id,
                "Nome", fonte == null ? "(nao encontrada)" : fonte.nome,
                "UnidadeEcad", fonte == null ? "" : fonte.unidadeEcad,
                "BaseStoragePath", fonte == null ? "" : fonte.baseStoragePath,
                "ChamadasIA", ia.size(),
                "CustoUsd", sumCost(ia),
                "TokensTotais", ia.stream().mapToInt(metric -> metric.tokensInput + metric.tokensOutput).sum(),
                "ExecucoesOperacionais", op.size(),
                "ItensProcessados", op.stream().mapToInt(metric -> metric.itensProcessados).sum(),
                "SucessoOperacionalPct", percent((int) op.stream().filter(metric -> metric.sucesso).count(), op.size()));
        }).toList();
        writeTabular(workbook, "Fontes", rows);
    }

    private static void buildTopFontesSheet(Workbook workbook, TelemetryDataset dataset) {
        var rows = fonteIds(dataset).stream().map(id -> {
            var ia = dataset.metricasIA().stream().filter(metric -> id.equals(metric.idFonteCaptacao)).toList();
            var op = dataset.metricasOperacionais().stream().filter(metric -> id.equals(metric.idFonteCaptacao)).toList();
            return row(
                "FonteId", id,
                "Nome", resolveFonteNome(dataset, id),
                "CustoUsd", sumCost(ia),
                "ChamadasIA", ia.size(),
                "LatenciaMediaIaMs", average(ia.stream().map(metric -> (double) metric.duracaoChamadaMs).toList()),
                "FalhasIA", ia.stream().filter(metric -> !metric.sucesso).count(),
                "FalhasOperacionais", op.stream().filter(metric -> !metric.sucesso).count(),
                "ItensProcessados", op.stream().mapToInt(metric -> metric.itensProcessados).sum());
        }).limit(50).toList();
        writeTabular(workbook, "Top Fontes", rows);
    }

    private static void buildFallbacksSheet(Workbook workbook, TelemetryDataset dataset) {
        var rows = dataset.metricasIA().stream().collect(Collectors.groupingBy(metric -> metric.idExecucao)).values().stream()
            .filter(items -> items.size() > 1 || items.stream().map(metric -> metric.provider).distinct().count() > 1)
            .map(items -> row(
                "Execucao", items.getFirst().idExecucao,
                "FonteId", items.getFirst().idFonteCaptacao,
                "Fonte", resolveFonteNome(dataset, items.getFirst().idFonteCaptacao),
                "Componente", text(items.getFirst().componente),
                "Operacao", text(items.getFirst().tipoOperacao),
                "Tentativas", items.size(),
                "ProvidersTentados", items.stream().map(metric -> text(metric.provider)).distinct().collect(Collectors.joining(" -> ")),
                "HouveFallback", items.stream().map(metric -> metric.provider).distinct().count() > 1,
                "SucessoFinal", items.stream().max(Comparator.comparing(metric -> metric.timestamp)).map(metric -> metric.sucesso).orElse(false),
                "CustoUsd", sumCost(items),
                "DuracaoTotalMs", items.stream().mapToLong(metric -> metric.duracaoChamadaMs).sum()))
            .toList();
        writeTabular(workbook, "Fallbacks IA", rows);
    }

    private static void buildAnomaliasSheet(Workbook workbook, TelemetryDataset dataset) {
        var p95Ia = percentile(dataset.metricasIA().stream().map(metric -> (double) metric.duracaoChamadaMs).toList(), 95);
        var p95Operacional = percentile(dataset.metricasOperacionais().stream().map(metric -> (double) metric.duracaoTotalMs).toList(), 95);
        var rows = new ArrayList<Map<String, Object>>();
        for (var metric : dataset.metricasIA()) {
            if (!metric.sucesso || metric.duracaoChamadaMs >= p95Ia) {
                rows.add(row("Tipo", metric.sucesso ? "Latencia IA p95+" : "Falha IA", "Timestamp", metric.timestamp, "FonteId", metric.idFonteCaptacao, "Fonte", resolveFonteNome(dataset, metric.idFonteCaptacao), "Componente", text(metric.componente), "Operacao", text(metric.tipoOperacao), "Resultado", metric.sucesso ? "sucesso_lento" : "falha", "Provider", text(metric.provider), "DuracaoMs", metric.duracaoChamadaMs, "ItensProcessados", 0, "Observacao", metric.modeloUtilizado));
            }
        }
        for (var metric : dataset.metricasOperacionais()) {
            if (!metric.sucesso || metric.duracaoTotalMs >= p95Operacional || metric.itensProcessados == 0) {
                rows.add(row("Tipo", metric.sucesso ? metric.itensProcessados == 0 ? "Sucesso sem itens" : "Latencia operacional p95+" : "Falha operacional", "Timestamp", metric.timestamp, "FonteId", metric.idFonteCaptacao, "Fonte", resolveFonteNome(dataset, metric.idFonteCaptacao), "Componente", text(metric.componente), "Operacao", metric.operacao, "Resultado", metric.resultado, "Provider", "", "DuracaoMs", metric.duracaoTotalMs, "ItensProcessados", metric.itensProcessados, "Observacao", ""));
            }
        }
        writeTabular(workbook, "Falhas e Anomalias", rows);
    }

    private static void buildIaDetailSheet(Workbook workbook, TelemetryDataset dataset) {
        var rows = dataset.metricasIA().stream()
            .sorted(Comparator.comparing((MetricaExecucaoIA metric) -> metric.timestamp).reversed())
            .map(metric -> row("Timestamp", metric.timestamp, "Execucao", metric.idExecucao, "FonteId", metric.idFonteCaptacao, "Fonte", resolveFonteNome(dataset, metric.idFonteCaptacao), "Componente", text(metric.componente), "Operacao", text(metric.tipoOperacao), "Documento", text(metric.tipoDocumento), "Provider", text(metric.provider), "Modelo", metric.modeloUtilizado, "TokensInput", metric.tokensInput, "TokensOutput", metric.tokensOutput, "CustoUsd", metric.custoUsd, "DuracaoChamadaMs", metric.duracaoChamadaMs, "Sucesso", metric.sucesso, "ResultadoDescarte", metric.resultadoDescarte))
            .toList();
        writeTabular(workbook, "Detalhe IA", rows);
    }

    private static void buildOperationalDetailSheet(Workbook workbook, TelemetryDataset dataset) {
        var rows = dataset.metricasOperacionais().stream()
            .sorted(Comparator.comparing((MetricaExecucaoOperacional metric) -> metric.timestamp).reversed())
            .map(metric -> row("Timestamp", metric.timestamp, "Execucao", metric.idExecucao, "FonteId", metric.idFonteCaptacao, "Fonte", resolveFonteNome(dataset, metric.idFonteCaptacao), "Componente", text(metric.componente), "Operacao", metric.operacao, "Resultado", metric.resultado, "Sucesso", metric.sucesso, "ItensProcessados", metric.itensProcessados, "DuracaoTotalMs", metric.duracaoTotalMs))
            .toList();
        writeTabular(workbook, "Detalhe Operacional", rows);
    }

    private static void writeTabular(Workbook workbook, String sheetName, List<Map<String, Object>> rows) {
        var sheet = workbook.createSheet(sheetName);
        var headers = rows.isEmpty() ? List.<String>of() : new ArrayList<>(rows.getFirst().keySet());
        var headerStyle = workbook.createCellStyle();
        var font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        headerStyle.setFont(font);
        headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        var borderStyle = workbook.createCellStyle();
        borderStyle.setBorderBottom(BorderStyle.THIN);
        borderStyle.setBorderTop(BorderStyle.THIN);
        borderStyle.setBorderLeft(BorderStyle.THIN);
        borderStyle.setBorderRight(BorderStyle.THIN);
        var header = sheet.createRow(0);
        for (var col = 0; col < headers.size(); col++) {
            var cell = header.createCell(col);
            cell.setCellValue(headers.get(col));
            cell.setCellStyle(headerStyle);
        }
        for (var rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            var row = sheet.createRow(rowIndex + 1);
            for (var col = 0; col < headers.size(); col++) {
                var cell = row.createCell(col);
                cell.setCellValue(stringValue(rows.get(rowIndex).get(headers.get(col))));
                cell.setCellStyle(borderStyle);
            }
        }
        sheet.createFreezePane(0, 1);
        for (var col = 0; col < headers.size(); col++) {
            sheet.autoSizeColumn(col);
        }
    }

    private static Map<String, Object> row(Object... values) {
        var row = new LinkedHashMap<String, Object>();
        for (var index = 0; index + 1 < values.length; index += 2) {
            row.put(values[index].toString(), values[index + 1]);
        }
        return row;
    }

    private static List<UUID> fonteIds(TelemetryDataset dataset) {
        var ids = new LinkedHashSet<UUID>();
        dataset.metricasIA().stream().map(metric -> metric.idFonteCaptacao).filter(id -> id != null).forEach(ids::add);
        dataset.metricasOperacionais().stream().map(metric -> metric.idFonteCaptacao).filter(id -> id != null).forEach(ids::add);
        ids.addAll(dataset.fontes().keySet());
        return List.copyOf(ids);
    }

    private static String resolveFonteNome(TelemetryDataset dataset, UUID fonteId) {
        var fonte = fonteId == null ? null : dataset.fontes().get(fonteId);
        return fonte == null ? "(nao encontrada)" : fonte.nome;
    }

    private static String topAverageIaComponent(List<MetricaExecucaoIA> metrics) {
        return metrics.stream().collect(Collectors.groupingBy(metric -> metric.componente)).entrySet().stream()
            .map(entry -> Map.entry(text(entry.getKey()), average(entry.getValue().stream().map(metric -> (double) metric.duracaoChamadaMs).toList())))
            .max(Map.Entry.comparingByValue())
            .map(entry -> entry.getKey() + " (" + entry.getValue() + " ms)")
            .orElse("");
    }

    private static BigDecimal sumCost(List<MetricaExecucaoIA> metrics) {
        return metrics.stream().map(metric -> metric.custoUsd == null ? BigDecimal.ZERO : metric.custoUsd).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(4, RoundingMode.HALF_UP);
    }

    private static double average(List<Double> values) {
        return values.isEmpty() ? 0d : round(values.stream().mapToDouble(Double::doubleValue).average().orElse(0d));
    }

    private static double percentile(List<Double> values, double percentile) {
        if (values.isEmpty()) {
            return 0d;
        }
        var ordered = values.stream().sorted().toList();
        var index = percentile / 100d * (ordered.size() - 1);
        var lower = (int) Math.floor(index);
        var upper = (int) Math.ceil(index);
        if (lower == upper) {
            return round(ordered.get(lower));
        }
        var weight = index - lower;
        return round(ordered.get(lower) + ((ordered.get(upper) - ordered.get(lower)) * weight));
    }

    private static double percent(int numerator, int denominator) {
        return denominator == 0 ? 0d : round(numerator * 100d / denominator);
    }

    private static double round(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String stringValue(Object value) {
        return value == null ? "" : value.toString();
    }
}
