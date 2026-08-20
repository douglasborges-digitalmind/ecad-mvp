package br.com.ecad.captacao.loganalyser;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;

record AnalyzerOptions(Path inputDirectory, Path metricasIaPath, Path metricasOperacionaisPath, Path fontesPath, Path outputFilePath) {
    static AnalyzerOptions parse(String[] args, Path currentDirectory) {
        var arguments = parseArgs(args);
        var inputDirectory = absolute(arguments.getOrDefault("input-dir", currentDirectory.resolve(".localdev").resolve("data").toString()), currentDirectory);
        var defaultOutput = currentDirectory.resolve(".artifacts").resolve("log-analysis")
            .resolve("Analise-Telemetria-ECAD-" + DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now()) + ".xlsx");
        var options = new AnalyzerOptions(
            inputDirectory,
            absolute(arguments.getOrDefault("metricas-ia", inputDirectory.resolve("metricas-ia.json").toString()), currentDirectory),
            absolute(arguments.getOrDefault("metricas-operacionais", inputDirectory.resolve("metricas-operacionais.json").toString()), currentDirectory),
            absolute(arguments.getOrDefault("fontes", inputDirectory.resolve("fontes-captacao.json").toString()), currentDirectory),
            absolute(arguments.getOrDefault("output", defaultOutput.toString()), currentDirectory));
        validate(options);
        return options;
    }

    private static void validate(AnalyzerOptions options) {
        if (!Files.exists(options.metricasIaPath())) {
            throw new IllegalArgumentException("Arquivo de metricas de IA nao encontrado: " + options.metricasIaPath());
        }
        if (!Files.exists(options.metricasOperacionaisPath())) {
            throw new IllegalArgumentException("Arquivo de metricas operacionais nao encontrado: " + options.metricasOperacionaisPath());
        }
    }

    private static HashMap<String, String> parseArgs(String[] args) {
        var result = new HashMap<String, String>();
        for (var index = 0; index < args.length; index++) {
            var current = args[index];
            if (!current.startsWith("--")) {
                continue;
            }
            if (index + 1 >= args.length || args[index + 1].startsWith("--")) {
                throw new IllegalArgumentException("Valor ausente para o argumento '" + current + "'.");
            }
            result.put(current.substring(2), args[++index]);
        }
        return result;
    }

    private static Path absolute(String path, Path currentDirectory) {
        var parsed = Path.of(path);
        return parsed.isAbsolute() ? parsed.normalize() : currentDirectory.resolve(parsed).normalize();
    }
}
