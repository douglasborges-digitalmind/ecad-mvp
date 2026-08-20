package br.com.ecad.captacao.processingengine;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import br.com.ecad.captacao.shared.JsonDefaults;
import br.com.ecad.captacao.shared.domain.entities.MetricaExecucaoIA;
import br.com.ecad.captacao.shared.domain.enums.ComponenteIA;
import br.com.ecad.captacao.shared.domain.enums.ProviderIA;
import br.com.ecad.captacao.shared.domain.enums.TipoEvidencia;
import br.com.ecad.captacao.shared.domain.enums.TipoOperacaoIA;
import br.com.ecad.captacao.shared.infrastructure.config.AiProviderSettings;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
class HttpAiProviderChain implements AiProviderChain {
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(300);

    private final ProcessingEngineSettings settings;
    private final HttpClient httpClient;
    private final ObjectMapper mapper;

    HttpAiProviderChain(ProcessingEngineSettings settings) {
        this.settings = settings;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        this.mapper = JsonDefaults.objectMapper();
    }

    @Override
    public AiProviderExecution processar(String prompt, byte[] mediaBytes, String mimeType, TipoEvidencia tipoDocumento, UUID idFonteCaptacao) throws Exception {
        var metricas = new ArrayList<MetricaExecucaoIA>();
        var idExecucao = UUID.randomUUID();
        Exception lastFailure = null;
        for (var provider : settings.getAiProviders()) {
            validateSupported(provider);
            if (!isConfigured(provider)) {
                continue;
            }
            var started = System.nanoTime();
            try {
                var response = callProvider(provider, prompt);
                metricas.add(toMetric(idExecucao, provider, tipoDocumento, idFonteCaptacao, prompt, response, started, true));
                return new AiProviderExecution(response, metricas);
            } catch (Exception ex) {
                lastFailure = ex;
                metricas.add(toMetric(idExecucao, provider, tipoDocumento, idFonteCaptacao, prompt, null, started, false));
            }
        }
        if (lastFailure != null) {
            throw lastFailure;
        }
        throw new IllegalStateException("AI_PROVIDER_CHAIN nao possui provider utilizavel com a configuracao atual.");
    }

    private AiResponse callProvider(String provider, String prompt) throws Exception {
        return switch (provider.toLowerCase(Locale.ROOT)) {
            case "openrouter" -> callOpenRouter(prompt);
            case "gemini", "gemini_nativo" -> callGemini(prompt);
            case "ollama" -> callOllama(prompt);
            case "azure_openai" -> callAzureOpenAi(prompt);
            default -> throw new IllegalStateException("Provider IA nao suportado: " + provider);
        };
    }

    private AiResponse callOpenRouter(String prompt) throws Exception {
        var ai = settings.aiProvider();
        var body = mapper.writeValueAsString(Map.of(
            "model", ai.openRouterModel(),
            "messages", List.of(Map.of("role", "user", "content", prompt)),
            "temperature", 0));
        var request = HttpRequest.newBuilder(uri(ai.openRouterBaseUrl(), "/chat/completions"))
            .timeout(REQUEST_TIMEOUT)
            .header("Authorization", "Bearer " + ai.openRouterApiKey())
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Provider IA 'openrouter' retornou HTTP " + response.statusCode()
                + " para " + request.uri()
                + " corpo=" + truncateBody(response.body()));
        }
        var node = mapper.readTree(response.body());
        var content = node.path("choices").path(0).path("message").path("content").asText("");
        var usage = node.path("usage");
        return new AiResponse(content, usage.path("prompt_tokens").asInt(0), usage.path("completion_tokens").asInt(0), ai.openRouterModel(), ProviderIA.OPEN_ROUTER, null);
    }

    private AiResponse callGemini(String prompt) throws Exception {
        var ai = settings.aiProvider();
        var body = mapper.writeValueAsString(Map.of(
            "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt))))));
        var endpoint = "/v1beta/models/" + ai.geminiModel() + ":generateContent?key=" + ai.geminiApiKey();
        var request = HttpRequest.newBuilder(uri("https://generativelanguage.googleapis.com", endpoint))
            .timeout(REQUEST_TIMEOUT)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
        var node = sendJson(request, "gemini");
        var content = node.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText("");
        var usage = node.path("usageMetadata");
        return new AiResponse(content, usage.path("promptTokenCount").asInt(0), usage.path("candidatesTokenCount").asInt(0), ai.geminiModel(), ProviderIA.GEMINI_NATIVO, null);
    }

    private AiResponse callOllama(String prompt) throws Exception {
        var ai = settings.aiProvider();
        var body = mapper.writeValueAsString(Map.of("model", ai.ollamaModel(), "prompt", prompt, "stream", false));
        var request = HttpRequest.newBuilder(uri(ai.ollamaBaseUrl(), "/api/generate"))
            .timeout(REQUEST_TIMEOUT)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
        var node = sendJson(request, "ollama");
        return new AiResponse(node.path("response").asText(""), 0, 0, ai.ollamaModel(), ProviderIA.OLLAMA, BigDecimal.ZERO);
    }

    private AiResponse callAzureOpenAi(String prompt) throws Exception {
        var ai = settings.aiProvider();
        var endpoint = ai.azureOpenAiEndpoint().trim();
        if (endpoint.endsWith("/")) {
            endpoint = endpoint.substring(0, endpoint.length() - 1);
        }
        var deployment = ai.azureOpenAiDeployment().trim();
        var apiVersion = ai.azureOpenAiApiVersion().trim();
        var url = endpoint + "/openai/deployments/" + deployment + "/chat/completions?api-version=" + apiVersion;
        var body = mapper.writeValueAsString(Map.of(
            "messages", List.of(Map.of("role", "user", "content", prompt))));
        var request = HttpRequest.newBuilder(URI.create(url))
            .timeout(REQUEST_TIMEOUT)
            .header("api-key", ai.azureOpenAiApiKey())
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
        var node = sendJson(request, "azure_openai");
        var content = node.path("choices").path(0).path("message").path("content").asText("");
        var usage = node.path("usage");
        return new AiResponse(content, usage.path("prompt_tokens").asInt(0), usage.path("completion_tokens").asInt(0), deployment, ProviderIA.AZURE_OPENAI, null);
    }

    private JsonNode sendJson(HttpRequest request, String provider) throws Exception {
        try {
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Provider IA '" + provider + "' retornou HTTP " + response.statusCode()
                    + " para " + request.uri()
                    + " corpo=" + truncateBody(response.body()));
            }
            return mapper.readTree(response.body());
        } catch (Exception ex) {
            throw new IllegalStateException("Falha ao chamar provider IA '" + provider + "' em " + request.uri() + ": " + failureMessage(ex), ex);
        }
    }

    private static String truncateBody(String body) {
        if (body == null) return "(vazio)";
        return body.length() <= 500 ? body : body.substring(0, 500) + "...";
    }

    private static String failureMessage(Exception ex) {
        if (ex == null) {
            return "erro desconhecido";
        }
        var detail = ex.getMessage();
        if (detail == null || detail.isBlank()) {
            detail = ex.getCause() != null ? ex.getCause().getMessage() : null;
        }
        if (detail == null || detail.isBlank()) {
            detail = ex.getClass().getSimpleName();
        }
        return ex.getClass().getSimpleName() + ": " + detail;
    }

    private boolean isConfigured(String provider) {
        return settings.aiProvider().isProviderConfigured(provider);
    }

    private static void validateSupported(String provider) {
        AiProviderSettings.validateSupported(provider);
    }

    private MetricaExecucaoIA toMetric(UUID idExecucao, String provider, TipoEvidencia tipoDocumento, UUID idFonteCaptacao, String prompt, AiResponse response, long started, boolean success) {
        var metrica = new MetricaExecucaoIA();
        metrica.idExecucao = idExecucao;
        metrica.componente = ComponenteIA.PROCESSING_ENGINE;
        metrica.tipoOperacao = TipoOperacaoIA.EXTRACAO_SEMANTICA;
        metrica.tipoDocumento = tipoDocumento;
        metrica.modeloUtilizado = response == null ? modelFor(provider) : response.model();
        metrica.provider = providerEnum(provider);
        metrica.tokensInput = response == null ? 0 : response.tokensInput();
        metrica.tokensOutput = response == null ? 0 : response.tokensOutput();
        metrica.custoUsd = response == null ? BigDecimal.ZERO : response.costUsd();
        metrica.tamanhoInputChars = prompt.length();
        metrica.duracaoChamadaMs = Math.max(0, (System.nanoTime() - started) / 1_000_000);
        metrica.idFonteCaptacao = idFonteCaptacao;
        metrica.sucesso = success;
        metrica.timestamp = OffsetDateTime.now();
        return metrica;
    }

    private String modelFor(String provider) {
        return settings.aiProvider().modelFor(provider);
    }

    private static ProviderIA providerEnum(String provider) {
        return AiProviderSettings.providerEnum(provider);
    }

    private static URI uri(String baseUrl, String path) {
        var normalizedBase = baseUrl == null ? "" : baseUrl.trim();
        if (normalizedBase.endsWith("/")) {
            normalizedBase = normalizedBase.substring(0, normalizedBase.length() - 1);
        }
        if (path == null || path.isBlank()) {
            return URI.create(normalizedBase);
        }
        var normalizedPath = path.startsWith("/") ? path : "/" + path;
        if (normalizedBase.endsWith(normalizedPath)) {
            return URI.create(normalizedBase);
        }
        return URI.create(normalizedBase + normalizedPath);
    }
}