package br.com.ecad.captacao.documentscraper;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import br.com.ecad.captacao.shared.contracts.ExecutarScraping;
import br.com.ecad.captacao.shared.contracts.Routes;
import br.com.ecad.captacao.shared.domain.enums.TipoEvidencia;
import br.com.ecad.captacao.shared.infrastructure.local.LocalDevelopmentSettings;
import br.com.ecad.captacao.shared.infrastructure.local.LocalMessageQueue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(
    classes = {EcadDocumentScraperApplication.class, LocalScrapingCommandConsumerTest.FakeBrowserConfiguration.class},
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
        "LOCAL_DEVELOPMENT_ENABLED=true",
        "DOCUMENT_SCRAPER_CONSUMER_ENABLED=false",
        "AI_PROVIDER_CHAIN="
    })
class LocalScrapingCommandConsumerTest {
    @TempDir
    static java.nio.file.Path localRoot;

    @Autowired
    LocalMessageQueue queue;

    @Autowired
    DocumentScraperSettings settings;

    @Autowired
    LocalDevelopmentSettings localDevelopment;

    @Autowired
    LocalScrapingCommandConsumerService consumer;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("LOCAL_DEVELOPMENT_ROOT", () -> localRoot.toString());
    }

    @Test
    void consomeComandoLocalPublicaDocumentoCapturado() throws Exception {
        var comando = new ExecutarScraping(
            "https://example.org/contrato",
            TipoEvidencia.CONTRATO_MUSICAL,
            "capturar",
            List.of("contrato musical"),
            "staging/teste",
            UUID.randomUUID(),
            UUID.randomUUID(),
            Map.of("DATA_ALVO", OffsetDateTime.now().toLocalDate().toString()));

        queue.enqueue(settings.scrapingCommandsTopic(), settings.localConsumerRoute(), comando);

        assertThat(consumer.consumeOnce()).isEqualTo(1);
        assertThat(Files.list(localDevelopment.getQueuePath(settings.capturedDocumentsTopic(), Routes.PROCESSING_ENGINE))
            .filter(path -> path.getFileName().toString().endsWith(".json"))).hasSize(1);
    }

    @TestConfiguration
    static class FakeBrowserConfiguration {
        @Bean
        @Primary
        HybridBrowserService fakeBrowser() {
            return new FakeBrowser();
        }
    }

    static class FakeBrowser implements HybridBrowserService {
        @Override
        public String getMarkdownHttp(String url) {
            return "";
        }

        @Override
        public String getMarkdownHttp(String url, String postBody) {
            return "";
        }

        @Override
        public String getMarkdown(String url, String waitForSelector, String fillSelector, String fillText, String expandSelector, String iframeSelector, String submitSelector, String evaluateJs) {
            return "";
        }

        @Override
        public byte[] download(String url) {
            return new byte[0];
        }

        @Override
        public byte[] captureScreenshot(String url) {
            return new byte[0];
        }

        @Override
        public java.util.List<String> discoverPncpDetailLinks(String url) {
            return java.util.List.of("https://pncp.gov.br/app/contratos/detalhe/1");
        }

        @Override
        public PncpDetailResult fetchPncpDetail(String detailUrl) {
            return new PncpDetailResult(
                "https://pncp.gov.br/app/contratos/detalhe/1.pdf",
                "Contrato Musical de Teste",
                "Contrato musical de teste para evento",
                "2026-01-01");
        }
    }
}
