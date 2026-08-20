package br.com.ecad.captacao.loganalyser;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import br.com.ecad.captacao.shared.JsonDefaults;
import br.com.ecad.captacao.shared.domain.entities.MetricaExecucaoIA;
import br.com.ecad.captacao.shared.domain.entities.MetricaExecucaoOperacional;
import com.fasterxml.jackson.core.type.TypeReference;

class TelemetryDataLoader {
    TelemetryDataset load(AnalyzerOptions options) throws Exception {
        var metricasIA = loadCollection(options.metricasIaPath(), new TypeReference<List<MetricaExecucaoIA>>() { });
        var metricasOperacionais = loadCollection(options.metricasOperacionaisPath(), new TypeReference<List<MetricaExecucaoOperacional>>() { });
        var fontesList = Files.exists(options.fontesPath())
            ? loadCollection(options.fontesPath(), new TypeReference<List<FonteCaptacaoResumo>>() { })
            : List.<FonteCaptacaoResumo>of();
        Map<UUID, FonteCaptacaoResumo> fontes = fontesList.stream()
            .filter(fonte -> fonte.id != null)
            .collect(Collectors.toMap(fonte -> fonte.id, Function.identity(), (left, right) -> right));
        return new TelemetryDataset(metricasIA, metricasOperacionais, fontes);
    }

    private static <T> List<T> loadCollection(Path path, TypeReference<List<T>> type) throws Exception {
        var json = Files.readString(path);
        return json == null || json.isBlank() ? List.of() : JsonDefaults.objectMapper().readValue(json, type);
    }
}
