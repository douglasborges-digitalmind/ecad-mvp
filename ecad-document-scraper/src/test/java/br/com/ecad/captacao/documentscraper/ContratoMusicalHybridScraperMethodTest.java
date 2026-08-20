package br.com.ecad.captacao.documentscraper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import br.com.ecad.captacao.shared.contracts.ExecutarScraping;
import br.com.ecad.captacao.shared.contracts.KeysMetadados;
import br.com.ecad.captacao.shared.domain.enums.TipoEvidencia;
import br.com.ecad.captacao.shared.infrastructure.blob.BlobDownload;
import br.com.ecad.captacao.shared.infrastructure.blob.BlobStorageService;
import org.junit.jupiter.api.Test;

class ContratoMusicalHybridScraperMethodTest {
    @Test
    void shouldUseCanonicalPncpUrlFromMunicipiosCatalogWhenMetadataIsAvailable() throws Exception {
        var browser = new RecordingBrowser();
        var method = new ContratoMusicalHybridScraperMethod(browser, new NoOpHybridDocumentStore());

        var result = method.processar(new ExecutarScraping(
            "https://pncp.gov.br/app/contratos?pagina=1&municipios=9999&status=vigente&ufs=SE",
            TipoEvidencia.CONTRATO_MUSICAL,
            "capturar contratos",
            List.of("show cache"),
            "staging/teste",
            UUID.randomUUID(),
            UUID.randomUUID(),
            Map.of(
                KeysMetadados.MUNICIPIO, "Aracaju",
                KeysMetadados.UF, "SE")));

        assertThat(result.sucesso()).isTrue();
        assertThat(result.itensProcessados()).isZero();
        assertThat(browser.searchUrls).singleElement().asString().contains("municipios=1755");
        assertThat(browser.searchUrls.getFirst()).contains("ufs=SE");
        assertThat(browser.searchUrls.getFirst()).contains("q=show+cache");
    }

    private static final class RecordingBrowser implements HybridBrowserService {
        private final List<String> searchUrls = new ArrayList<>();

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
        public List<String> discoverPncpDetailLinks(String url) {
            searchUrls.add(url);
            return List.of();
        }

        @Override
        public PncpDetailResult fetchPncpDetail(String detailUrl) {
            return null;
        }
    }
    private static final class NoOpHybridDocumentStore extends HybridDocumentStore {
        NoOpHybridDocumentStore() {
            super(new NoOpDocumentoRepository(), new BlobStorageService() {
                @Override
                public String uploadStaging(byte[] content, String stagingPath, String fileName) {
                    return "file:/noop/" + fileName;
                }

                @Override
                public BlobDownload download(String blobUrl) {
                    return new BlobDownload(new byte[0], "application/octet-stream");
                }

                @Override
                public String moveToProduction(String stagingUrl) {
                    return stagingUrl.replace("staging", "producao");
                }

                @Override
                public void delete(String blobUrl) {
                }
            }, documento -> {
            });
        }
    }
}