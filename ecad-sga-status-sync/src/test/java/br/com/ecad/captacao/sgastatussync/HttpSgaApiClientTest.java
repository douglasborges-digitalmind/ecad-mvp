package br.com.ecad.captacao.sgastatussync;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

import br.com.ecad.captacao.shared.domain.enums.StatusSGA;
import br.com.ecad.captacao.shared.infrastructure.sga.SgaConnectionSettings;
import br.com.ecad.captacao.shared.infrastructure.sga.SgaCredentialsProvider;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class HttpSgaApiClientTest {
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void fetchesShowsWithAuthorizationAndCachesByDateUfMunicipio() throws Exception {
        var calls = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/show/v2/shows", exchange -> {
            calls.incrementAndGet();
            assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer ******");
            assertThat(exchange.getRequestURI().getQuery()).contains("uf=SP", "codMunicipio=3550308", "limit=600");
            writeJson(exchange, "{\"body\":[{\"titulo\":\"Festival Primavera\",\"dataPrevista\":\"2026-03-12T00:00:00Z\",\"municipio\":\"S\\u00e3o Paulo\",\"codigo\":\"A1\",\"status\":\"ATIVO\"}]}");
        });
        server.start();

        var settings = settings("http://127.0.0.1:" + server.getAddress().getPort() + "/show/v2", "******");
        // Como o teste usa SGA_AUTHORIZATION estatico (token direto), o SgaCredentialsProvider
        // nunca faz chamada OAuth; instanciamos com settings dummy.
        var credentialsProvider = new SgaCredentialsProvider(SgaConnectionSettings.of("", "", "", "", "******", ""));
        var client = new HttpSgaApiClient(HttpClient.newHttpClient(), settings, new SgaEventMatcher(), credentialsProvider);
        var query = new SgaEventQuery("Festival Primavera", LocalDate.of(2026, 3, 12), "Sao Paulo", "SP", 3550308);

        var first = client.verificarEvento(query);
        var second = client.verificarEvento(query);

        assertThat(first.status()).isEqualTo(StatusSGA.JA_CADASTRADO);
        assertThat(first.fromCache()).isFalse();
        assertThat(second.fromCache()).isTrue();
        assertThat(calls).hasValue(1);
    }

    private static SgaStatusSyncSettings settings(String baseUrl, String authorization) {
        // Ordem do record SgaStatusSyncSettings(15 campos):
        // 1 sgaOAuthUrl, 2 sgaBaseUrl, 3 sgaClientId, 4 sgaClientSecret, 5 sgaAuthorization,
        // 6 sgaUser, 7 municipioCsvPath, 8 sgaVerificationEnabled, 9 sgaTimeoutSeconds,
        // 10 sgaMaxRetries, 11 rateLimitDelayMs, 12 sgaResultLimit, 13 concurrency,
        // 14 mongoConnectionString, 15 mongoDatabaseName.
        // Campos nao exercitados pelo teste recebem defaults seguros.
        return new SgaStatusSyncSettings(
            "http://unused/oauth",
            baseUrl,
            "",
            "",
            authorization,
            "",
            "",
            true,
            5,
            1,
            0,
            600,
            4,
            "",
            "ecad-captacao");
    }

    private static void writeJson(com.sun.net.httpserver.HttpExchange exchange, String json) throws IOException {
        var bytes = json.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (var body = exchange.getResponseBody()) {
            body.write(bytes);
        }
    }
}