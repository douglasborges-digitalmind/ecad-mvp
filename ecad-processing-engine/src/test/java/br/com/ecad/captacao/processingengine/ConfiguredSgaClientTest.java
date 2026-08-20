package br.com.ecad.captacao.processingengine;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Map;

import br.com.ecad.captacao.shared.domain.enums.StatusSGA;
import br.com.ecad.captacao.shared.infrastructure.config.AiProviderSettings;
import br.com.ecad.captacao.shared.infrastructure.sga.SgaConnectionSettings;
import br.com.ecad.captacao.shared.infrastructure.sga.SgaCredentialsProvider;
import org.junit.jupiter.api.Test;

class ConfiguredSgaClientTest {
    @Test
    void retornaNaoVerificadoSemEnviarHttpQuandoSgaEstaDesabilitado() throws Exception {
        var transport = new RecordingTransport();
        var client = new ConfiguredSgaClient(
            settings(false, "", "", "", "", "", 1),
            transport,
            new StubSgaCredentialsProvider("nao-deve-ser-usado"));

        var status = client.verificar("Show", "Arena", "2026-01-01", "2026-01-01", "BA");

        assertThat(status).isEqualTo(StatusSGA.NAO_VERIFICADO);
        assertThat(transport.requests).isEmpty();
    }

    @Test
    void usaOAuthEConsultaShowsDoSga() throws Exception {
        var transport = new RecordingTransport(
            new SgaHttpResponse(200, "{\"resultSize\":1}"));
        var client = new ConfiguredSgaClient(
            settings(true, "https://sga.example/api-show", "https://sga.example/oauth", "client", "secret", "usuario", 1),
            transport,
            new StubSgaCredentialsProvider("token-sga"));

        var status = client.verificar("Show Teste", "Praca Central", "2026-04-05T10:00:00Z", "", "BA");

        assertThat(status).isEqualTo(StatusSGA.JA_CADASTRADO);
        assertThat(transport.requests).hasSize(1);
        assertThat(transport.requests.get(0).method()).isEqualTo("GET");
        assertThat(transport.requests.get(0).uri().toString()).contains("/shows?nome=Show+Teste");
        assertThat(transport.requests.get(0).headers()).containsEntry("Authorization", "Bearer token-sga");
        assertThat(transport.requests.get(0).headers()).containsEntry("USER", "usuario");
    }

    @Test
    void usaAuthorizationDiretoEMapeiaResultadoInedito() throws Exception {
        var transport = new RecordingTransport(new SgaHttpResponse(200, "{\"resultSize\":0}"));
        var client = new ConfiguredSgaClient(
            settings(true, "https://sga.example/api-show", "", "", "", "Bearer token-direto", 1),
            transport,
            new StubSgaCredentialsProvider("token-direto"));

        var status = client.verificar("Show", "Arena", "2026-01-01", "2026-01-02", "SP");

        assertThat(status).isEqualTo(StatusSGA.INEDITO);
        assertThat(transport.requests).hasSize(1);
        assertThat(transport.requests.get(0).headers()).containsEntry("Authorization", "Bearer token-direto");
    }

    /**
     * Constrói um {@link ProcessingEngineSettings} mínimo com apenas os campos
     * relevantes para o cenario SGA. O resto recebe defaults seguros (vazio / false / 0)
     * porque estes testes nao exercitam pipeline IA nem persistencia.
     */
    private static ProcessingEngineSettings settings(boolean sgaEnabled, String sgaBaseUrl, String sgaOauthUrl,
                                                     String sgaClientId, String sgaClientSecret, String sgaUserOrAuth,
                                                     int sgaMaxRetries) {
        return new ProcessingEngineSettings(
            "", "", "", "", "", "", "captured_documents", "processing-engine", "processing-engine", 5,
            "cg-processing-engine",
            "", "ecad-captacao",
            "", "",  // azureStorageConnectionString, azureBlobContainerName
            "staging/", "producao/",
            3, new AiProviderSettings("ollama", "", "https://openrouter.ai/api/v1", "google/gemini-3.1-flash-lite-preview", "", "gemini-3.1-flash-lite-preview", "http://localhost:11434", "llama3.2-vision:11b", "", "", "", ""),
            sgaEnabled, sgaOauthUrl, sgaBaseUrl, sgaUserOrAuth, sgaClientId, sgaClientSecret,
            "usuario".equals(sgaUserOrAuth) ? sgaUserOrAuth : "", 30, sgaMaxRetries, 3_600_000, 1);
    }

    private static class RecordingTransport implements SgaHttpTransport {
        private final ArrayList<Request> requests = new ArrayList<>();
        private final ArrayList<SgaHttpResponse> responses = new ArrayList<>();

        RecordingTransport(SgaHttpResponse... responses) {
            this.responses.addAll(java.util.List.of(responses));
        }

        @Override
        public SgaHttpResponse send(String method, URI uri, Map<String, String> headers, String body, Duration timeout) {
            requests.add(new Request(method, uri, Map.copyOf(headers), body));
            return responses.isEmpty() ? new SgaHttpResponse(200, "{\"resultSize\":0}") : responses.remove(0);
        }
    }

    private record Request(String method, URI uri, Map<String, String> headers, String body) {
    }

    /**
     * Stub determinístico de {@link SgaCredentialsProvider} usado para isolar o teste
     * do mecanismo real de OAuth (que faria uma chamada HTTP extra ao endpoint de token).
     * <p>
     * Retorna sempre o token fixo configurado no construtor, sem cache, sem retry, sem
     * HTTP. O {@link SgaConnectionSettings} é repassado com defaults para satisfazer a
     * superclasse.
     */
    private static final class StubSgaCredentialsProvider extends SgaCredentialsProvider {
        private final String token;

        StubSgaCredentialsProvider(String token) {
            super(SgaConnectionSettings.of(null, null, null, null, null, null));
            this.token = token;
        }

        @Override
        public String getToken() {
            return token;
        }
    }
}
