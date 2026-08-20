package br.com.ecad.captacao.loganalyser;

import java.nio.file.Files;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class EcadLogAnalyserApplication {
    private static final Logger LOGGER = LoggerFactory.getLogger(EcadLogAnalyserApplication.class);

    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            var options = AnalyzerOptions.parse(args, Path.of(System.getProperty("user.dir")));
            var dataset = new TelemetryDataLoader().load(options);
            var bytes = new TelemetryWorkbookBuilder().build(dataset, options);
            Files.createDirectories(options.outputFilePath().getParent());
            Files.write(options.outputFilePath(), bytes);
            LOGGER.info("Planilha gerada com sucesso em: {}", options.outputFilePath());
            LOGGER.info("Metricas IA: {}", dataset.metricasIA().size());
            LOGGER.info("Metricas operacionais: {}", dataset.metricasOperacionais().size());
            LOGGER.info("Fontes carregadas: {}", dataset.fontes().size());
            return;
        }
        SpringApplication.run(EcadLogAnalyserApplication.class, args);
    }
}
