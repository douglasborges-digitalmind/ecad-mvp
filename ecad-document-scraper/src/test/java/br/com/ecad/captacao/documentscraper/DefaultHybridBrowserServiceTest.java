package br.com.ecad.captacao.documentscraper;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class DefaultHybridBrowserServiceTest {
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void converteHtmlParaMarkdownDescobreDetalheEBaixaPdf() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/page", exchange -> respond(exchange, "<html><body><a href=\"/detail\">Detalhe</a></body></html>", "text/html"));
        server.createContext("/detail", exchange -> respond(exchange, "<a href=\"/docs/contrato.pdf\">PDF</a>", "text/html"));
        server.createContext("/docs/contrato.pdf", exchange -> respond(exchange, new byte[] {4, 5, 6}, "application/pdf"));
        server.start();
        var browser = new DefaultHybridBrowserService(HttpClient.newHttpClient(), new StubPlaywrightBrowserPool());
        var baseUrl = "http://localhost:" + server.getAddress().getPort();

        var markdown = browser.getMarkdownHttp(baseUrl + "/page");
        var detalhes = browser.discoverPncpDetailLinks(baseUrl + "/page");
        var detail = browser.fetchPncpDetail(baseUrl + "/detail");
        var bytes = browser.download(baseUrl + "/docs/contrato.pdf");

        assertThat(markdown).contains(baseUrl + "/detail");
        assertThat(detalhes).containsExactly(baseUrl + "/detail");
        assertThat(detail).isNotNull();
        assertThat(detail.pdfUrl()).isEqualTo(baseUrl + "/docs/contrato.pdf");
        assertThat(bytes).containsExactly(4, 5, 6);
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, String body, String contentType) throws java.io.IOException {
        respond(exchange, body.getBytes(StandardCharsets.UTF_8), contentType);
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, byte[] body, String contentType) throws java.io.IOException {
        exchange.getResponseHeaders().add("Content-Type", contentType);
        exchange.sendResponseHeaders(200, body.length);
        try (var output = exchange.getResponseBody()) {
            output.write(body);
        }
    }

    /**
     * Stub do {@link PlaywrightBrowserPool} usado por testes que só exercitam os caminhos
     * baseados em {@link HttpClient} (HTTP, descoberta de links, download de PDF).
     * <p>
     * O {@link DefaultHybridBrowserService#getMarkdown(String, String, String, String, String, String, String, String)}
     * é a única operação que precisa de um browser real; como este teste não a invoca,
     * o stub lança uma exceção clara caso seja chamado acidentalmente.
     */
    private static final class StubPlaywrightBrowserPool extends PlaywrightBrowserPool {
        @Override
        com.microsoft.playwright.Browser getBrowser() {
            throw new UnsupportedOperationException("StubPlaywrightBrowserPool nao fornece browser real; o teste atual nao exercita o caminho Playwright.");
        }

        @Override
        void close() {
            // Sem recursos para liberar: o Playwright nunca foi instanciado.
        }
    }
}
