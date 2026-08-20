package br.com.ecad.captacao.documentscraper;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import br.com.ecad.captacao.shared.contracts.ExecutarScraping;
import br.com.ecad.captacao.shared.domain.entities.MetricaExecucaoOperacional;
import br.com.ecad.captacao.shared.domain.enums.ComponenteIA;
import br.com.ecad.captacao.shared.infrastructure.repositories.MetricaOperacionalRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

@Service
class HybridScrapingPipeline {
    private static final Logger LOGGER = LoggerFactory.getLogger(HybridScrapingPipeline.class);

    private final List<HybridScraperMethod> scraperMethods;
    private final MetricaOperacionalRepository metricas;

    HybridScrapingPipeline(List<HybridScraperMethod> scraperMethods, MetricaOperacionalRepository metricas) {
        this.scraperMethods = scraperMethods;
        this.metricas = metricas;
    }

    HybridScrapingResult processarComando(ExecutarScraping comando) throws Exception {
        var started = System.nanoTime();
        var idExecucao = UUID.randomUUID();
        try (var ignoredCorr = MDC.putCloseable("correlation_id", idExecucao.toString());
             var ignoredFonte = MDC.putCloseable("id_fonte_captacao", String.valueOf(comando.idFonteCaptacao()));
             var ignoredTipo = MDC.putCloseable("tipo_alvo", String.valueOf(comando.tipoAlvo()))) {
            LOGGER.info("scraping_iniciado url={} palavras_chave={}", comando.urlAlvo(),
                comando.palavrasChavesBusca() == null ? 0 : comando.palavrasChavesBusca().size());
            var method = scraperMethods.stream().filter(candidate -> candidate.canHandle(comando)).findFirst();
            if (method.isEmpty()) {
                LOGGER.warn("scraping_sem_handler tipoAlvo={}", comando.tipoAlvo());
                var result = new HybridScrapingResult(false, 0, "no_handler");
                salvarMetrica(idExecucao, comando, started, result);
                return result;
            }

            try {
                var result = method.get().processar(comando);
                LOGGER.info("scraping_concluido sucesso={} processados={} descobertos={} filtrados={} baixados={} persistidos={} resultado={}",
                    result.sucesso(), result.itensProcessados(), result.itensDescobertos(), result.itensFiltrados(),
                    result.itensBaixados(), result.itensPersistidos(), result.resultado());
                salvarMetrica(idExecucao, comando, started, result);
                return result;
            } catch (Exception ex) {
                LOGGER.error("scraping_falhou url={}", comando.urlAlvo(), ex);
                var result = new HybridScrapingResult(false, 0, "error: " + ex.getMessage());
                salvarMetrica(idExecucao, comando, started, result);
                return result;
            }
        }
    }

    private void salvarMetrica(UUID idExecucao, ExecutarScraping comando, long started, HybridScrapingResult result) throws Exception {
        var metrica = new MetricaExecucaoOperacional();
        metrica.idExecucao = idExecucao;
        metrica.componente = ComponenteIA.DOCUMENT_SCRAPER;
        metrica.operacao = "pipeline_scraping_hybrid";
        metrica.duracaoTotalMs = Math.max(0, (System.nanoTime() - started) / 1_000_000);
        metrica.sucesso = result.sucesso();
        metrica.resultado = result.resultado();
        metrica.itensProcessados = result.itensProcessados();
        metrica.itensDescobertos = result.itensDescobertos();
        metrica.itensFiltrados = result.itensFiltrados();
        metrica.itensBaixados = result.itensBaixados();
        metrica.itensPersistidos = result.itensPersistidos();
        metrica.idFonteCaptacao = comando.idFonteCaptacao();
        metrica.timestamp = OffsetDateTime.now();
        metricas.salvar(metrica);
    }
}
