package br.com.ecad.captacao.deduplicator;

import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class EcadDeduplicatorApplication {
    private static final Logger LOGGER = LoggerFactory.getLogger(EcadDeduplicatorApplication.class);

    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            var settings = DeduplicationSettings.fromArgs(args, Path.of(System.getProperty("user.dir")));
            var result = new EventoDeduplicationService(NullAiDuplicateDecider.INSTANCE, settings).execute();
            LOGGER.info("Deduplicacao concluida. Lidos={} Finais={} Mesclados={} DryRun={}",
                result.totalRead(), result.totalWritten(), result.mergedGroups().size(), result.dryRun());
            return;
        }
        SpringApplication.run(EcadDeduplicatorApplication.class, args);
    }
}
