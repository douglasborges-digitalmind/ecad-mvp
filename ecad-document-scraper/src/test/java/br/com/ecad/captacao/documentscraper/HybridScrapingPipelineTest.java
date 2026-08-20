package br.com.ecad.captacao.documentscraper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import br.com.ecad.captacao.shared.contracts.ExecutarScraping;
import br.com.ecad.captacao.shared.domain.enums.TipoEvidencia;
import org.junit.jupiter.api.Test;

class HybridScrapingPipelineTest {
    @Test
    void selecionaMetodoCompativelESalvaMetrica() throws Exception {
        var metricas = new NoOpMetricaOperacionalRepository();
        var pipeline = new HybridScrapingPipeline(List.of(new FakeMethod()), metricas);

        var result = pipeline.processarComando(comando(TipoEvidencia.CONTRATO_MUSICAL));

        assertThat(result).isEqualTo(new HybridScrapingResult(true, 2, "ok"));
        assertThat(metricas.saved).hasSize(1);
        assertThat(metricas.saved.getFirst().sucesso).isTrue();
        assertThat(metricas.saved.getFirst().itensProcessados).isEqualTo(2);
    }

    @Test
    void retornaNoHandlerQuandoNaoHaMetodoCompativel() throws Exception {
        var metricas = new NoOpMetricaOperacionalRepository();
        var pipeline = new HybridScrapingPipeline(List.of(new FakeMethodIncompativel()), metricas);

        var result = pipeline.processarComando(comando(TipoEvidencia.CONTRATO_MUSICAL));

        assertThat(result).isEqualTo(new HybridScrapingResult(false, 0, "no_handler"));
        assertThat(metricas.saved).hasSize(1);
        assertThat(metricas.saved.getFirst().sucesso).isFalse();
    }

    private static ExecutarScraping comando(TipoEvidencia tipo) {
        return new ExecutarScraping(
            "https://example.org",
            tipo,
            "capturar",
            List.of(),
            "staging/teste",
            UUID.randomUUID(),
            UUID.randomUUID(),
            Map.of());
    }

    private static class FakeMethod implements HybridScraperMethod {
        @Override
        public boolean canHandle(ExecutarScraping comando) {
            return comando.tipoAlvo() == TipoEvidencia.CONTRATO_MUSICAL;
        }

        @Override
        public HybridScrapingResult processar(ExecutarScraping comando) {
            return new HybridScrapingResult(true, 2, "ok");
        }
    }

    private static class FakeMethodIncompativel implements HybridScraperMethod {
        @Override
        public boolean canHandle(ExecutarScraping comando) {
            return false;
        }

        @Override
        public HybridScrapingResult processar(ExecutarScraping comando) {
            return new HybridScrapingResult(true, 2, "ok");
        }
    }
}
