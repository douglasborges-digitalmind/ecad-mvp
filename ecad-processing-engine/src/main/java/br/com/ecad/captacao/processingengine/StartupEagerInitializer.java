package br.com.ecad.captacao.processingengine;

import br.com.ecad.captacao.shared.infrastructure.repositories.CriterioExtracaoRepository;
import br.com.ecad.captacao.shared.infrastructure.repositories.MetricaIARepository;
import br.com.ecad.captacao.shared.infrastructure.repositories.MetricaOperacionalRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Garante que os repositorios sao instanciados e seed aplicado no startup,
 * independente da chegada de mensagens do Kafka. Sem isso, o ensureSeed()
 * do CriterioExtracaoRepository/MunicipioUnidade so roda quando o pipeline
 * processa o primeiro documento, deixando o app em estado inconsistente ate la.
 */
@Component
class StartupEagerInitializer implements ApplicationRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(StartupEagerInitializer.class);

    private final CriterioExtracaoRepository criterios;
    private final MetricaIARepository metricasIa;
    private final MetricaOperacionalRepository metricasOperacional;

    StartupEagerInitializer(
        CriterioExtracaoRepository criterios,
        MetricaIARepository metricasIa,
        MetricaOperacionalRepository metricasOperacional) {
        this.criterios = criterios;
        this.metricasIa = metricasIa;
        this.metricasOperacional = metricasOperacional;
    }

    @Override
    public void run(ApplicationArguments args) {
        LOGGER.info("Eager init: forçando instanciacao dos repositorios (seed de criterios-extracao).");
        LOGGER.info("Eager init OK: criterios={}, metricas_ia={}, metricas_operacionais={}",
            criterios.getClass().getSimpleName(),
            metricasIa.getClass().getSimpleName(),
            metricasOperacional.getClass().getSimpleName());
    }
}
