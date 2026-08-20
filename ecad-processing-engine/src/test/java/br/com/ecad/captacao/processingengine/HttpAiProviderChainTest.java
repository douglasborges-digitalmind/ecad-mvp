package br.com.ecad.captacao.processingengine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.UUID;

import br.com.ecad.captacao.shared.domain.enums.ProviderIA;
import br.com.ecad.captacao.shared.domain.enums.TipoEvidencia;
import br.com.ecad.captacao.shared.infrastructure.config.AiProviderSettings;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class HttpAiProviderChainTest {
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void reutilizaMesmoIdExecucaoQuandoHaFallbackDeProvider() throws Exception {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/chat/completions", exchange -> respond(exchange, 500, "{}"));
        server.createContext("/api/generate", exchange -> respond(exchange, 200, "{\"response\":\"{\\\"evento_identificado\\\":false}\"}"));
        server.start();
        var baseUrl = "http://localhost:" + server.getAddress().getPort();
        var chain = new HttpAiProviderChain(settings("openrouter,ollama", "fake-api-key-1234", baseUrl, baseUrl, "google/gemini-3.1-flash-lite-preview", "", "http://localhost:11434", "llama3.2-vision:11b"));

        var execution = chain.processar("prompt", new byte[] {1, 2}, "application/pdf", TipoEvidencia.CONTRATO_MUSICAL, UUID.randomUUID());

        assertThat(execution.response().content()).contains("evento_identificado");
        assertThat(execution.metricas()).hasSize(2);
        assertThat(execution.metricas().get(0).idExecucao).isEqualTo(execution.metricas().get(1).idExecucao);
        assertThat(execution.metricas().get(0).sucesso).isFalse();
        assertThat(execution.metricas().get(1).sucesso).isTrue();
    }

    @Test
    void aceitaBaseUrlDoOpenRouterQueJaIncluiOEndpointDeChatCompletions() throws Exception {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        var requestedPaths = new java.util.ArrayList<String>();
        server.createContext("/", exchange -> {
            requestedPaths.add(exchange.getRequestURI().getPath());
            respond(exchange, 200, "{\"choices\":[{\"message\":{\"content\":\"{\\\"evento_identificado\\\":false}\"}}],\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":1}}" );
        });
        server.start();
        var baseUrl = "http://localhost:" + server.getAddress().getPort() + "/chat/completions";
        var chain = new HttpAiProviderChain(settings("openrouter", "fake-api-key-1234", baseUrl, "http://localhost:11434", "google/gemini-3.1-flash-lite-preview", "", "http://localhost:11434", "llama3.2-vision:11b"));

        var execution = chain.processar("prompt", new byte[] {1, 2}, "application/pdf", TipoEvidencia.CONTRATO_MUSICAL, UUID.randomUUID());

        assertThat(execution.response().content()).contains("evento_identificado");
        assertThat(requestedPaths).containsExactly("/chat/completions");
    }

    @Test
    void incluiDetalhesDoProviderQuandoFalhaAConexaoComOllama() {
        var chain = new HttpAiProviderChain(settings("ollama", "", "https://openrouter.ai/api/v1", "http://127.0.0.1:1", "google/gemini-3.1-flash-lite-preview", "", "http://localhost:11434", "llama3.2-vision:11b"));

        assertThatThrownBy(() -> chain.processar("prompt", new byte[0], "text/plain", TipoEvidencia.CONTRATO_MUSICAL, UUID.randomUUID()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("ollama")
            .hasMessageContaining("127.0.0.1:1")
            .hasMessageContaining("ConnectException");
    }

    @Test
    void falhaComNomeDoProviderQuandoProviderNaoEReconhecido() {
        var chain = new HttpAiProviderChain(settings("provedor-x", "", "https://openrouter.ai/api/v1", "http://localhost:11434", "google/gemini-3.1-flash-lite-preview", "", "http://localhost:11434", "llama3.2-vision:11b"));

        assertThatThrownBy(() -> chain.processar("prompt", new byte[0], "text/plain", TipoEvidencia.CONTRATO_MUSICAL, UUID.randomUUID()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("provedor-x");
    }

    /**
     * Constrói um {@link ProcessingEngineSettings} via construtor canônico do record.
     * <p>
     * O {@link HttpAiProviderChain} consulta os campos de IA ({@code aiProviderChain},
     * {@code openRouterApiKey}, {@code openRouterBaseUrl}, {@code geminiApiKey},
     * {@code geminiModel}, {@code ollamaBaseUrl}, {@code ollamaModel}); os demais 25
     * campos do record (EventHubs, Cosmos, Blob, SGA) recebem defaults seguros
     * porque o chain nao consulta nada alem de providers de IA.
     */
    private static ProcessingEngineSettings settings(String aiProviderChain, String openRouterApiKey, String openRouterBaseUrl,
                                                    String ollamaBaseUrl, String openRouterModel, String geminiApiKey,
                                                    String geminiBaseUrl, String ollamaModel) {
        return new ProcessingEngineSettings(
            "", "", "", "", "", "", "captured_documents", "processing-engine", "processing-engine", 5,
            "cg-processing-engine",
            "", "ecad-captacao",
            "", "",  // azureStorageConnectionString, azureBlobContainerName
            "staging/", "producao/",
            3, new AiProviderSettings(aiProviderChain, openRouterApiKey, openRouterBaseUrl, openRouterModel, geminiApiKey, geminiBaseUrl, ollamaBaseUrl, ollamaModel, "", "", "", ""),
            false, "", "", "", "", "", "", 30, 3, 3_600_000, 1);
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        var bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream stream = exchange.getResponseBody()) {
            stream.write(bytes);
        }
    }

    @Test
    void usaAzureOpenAiNaCadeiaQuandoConfigurado() throws Exception {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/openai/deployments/gpt-4o/chat/completions", exchange -> respond(exchange, 200,
            "{\"choices\":[{\"message\":{\"content\":\"{\\\"evento_identificado\\\":false}\"}}],\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":5}}"));
        server.start();
        var endpoint = "http://localhost:" + server.getAddress().getPort();
        var chain = new HttpAiProviderChain(settings("azure_openai", "", "https://openrouter.ai/api/v1",
            "http://localhost:11434", "google/gemini-3.1-flash-lite-preview", "",
            "gemini-3.1-flash-lite-preview", "llama3.2-vision:11b",
            endpoint, "fake-api-key-1234", "gpt-4o", "2024-10-21"));

        var execution = chain.processar("prompt", new byte[] {1, 2}, "application/pdf", TipoEvidencia.CONTRATO_MUSICAL, UUID.randomUUID());

        assertThat(execution.response().content()).contains("evento_identificado");
        assertThat(execution.response().provider()).isEqualTo(ProviderIA.AZURE_OPENAI);
        assertThat(execution.response().model()).isEqualTo("gpt-4o");
        assertThat(execution.response().tokensInput()).isEqualTo(10);
        assertThat(execution.response().tokensOutput()).isEqualTo(5);
        assertThat(execution.metricas()).hasSize(1);
        assertThat(execution.metricas().get(0).sucesso).isTrue();
    }

    @Test
    void azureOpenAiFalhaEFazFallbackParaProximoProvider() throws Exception {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/openai/deployments/gpt-4o/chat/completions", exchange -> respond(exchange, 500, "{}"));
        server.createContext("/api/generate", exchange -> respond(exchange, 200, "{\"response\":\"{\\\"evento_identificado\\\":false}\"}"));
        server.start();
        var endpoint = "http://localhost:" + server.getAddress().getPort();
        var ollamaUrl = "http://localhost:" + server.getAddress().getPort();
        var chain = new HttpAiProviderChain(settings("azure_openai,ollama", "", "https://openrouter.ai/api/v1",
            ollamaUrl, "google/gemini-3.1-flash-lite-preview", "",
            "gemini-3.1-flash-lite-preview", "llama3.2-vision:11b",
            endpoint, "fake-api-key-1234", "gpt-4o", "2024-10-21"));

        var execution = chain.processar("prompt", new byte[] {1, 2}, "application/pdf", TipoEvidencia.CONTRATO_MUSICAL, UUID.randomUUID());

        assertThat(execution.response().content()).contains("evento_identificado");
        assertThat(execution.response().provider()).isEqualTo(ProviderIA.OLLAMA);
        assertThat(execution.metricas()).hasSize(2);
        assertThat(execution.metricas().get(0).sucesso).isFalse();
        assertThat(execution.metricas().get(1).sucesso).isTrue();
    }

    /**
     * Overload de settings que aceita os campos do Azure OpenAI.
     */
    private static ProcessingEngineSettings settings(String aiProviderChain, String openRouterApiKey, String openRouterBaseUrl,
                                                    String ollamaBaseUrl, String openRouterModel, String geminiApiKey,
                                                    String geminiBaseUrl, String ollamaModel,
                                                    String azureOpenAiEndpoint, String azureOpenAiApiKey,
                                                    String azureOpenAiDeployment, String azureOpenAiApiVersion) {
        return new ProcessingEngineSettings(
            "", "", "", "", "", "", "captured_documents", "processing-engine", "processing-engine", 5,
            "cg-processing-engine",
            "", "ecad-captacao",
            "", "",  // azureStorageConnectionString, azureBlobContainerName
            "staging/", "producao/",
            3, new AiProviderSettings(aiProviderChain, openRouterApiKey, openRouterBaseUrl, openRouterModel, geminiApiKey, geminiBaseUrl, ollamaBaseUrl, ollamaModel, azureOpenAiEndpoint, azureOpenAiApiKey, azureOpenAiDeployment, azureOpenAiApiVersion),
            false, "", "", "", "", "", "", 30, 3, 3_600_000, 1);
    }
}