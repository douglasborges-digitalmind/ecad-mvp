package br.com.ecad.captacao.shared.infrastructure.config;

import java.util.Arrays;
import java.util.Locale;

import br.com.ecad.captacao.shared.domain.enums.ProviderIA;
import org.springframework.core.env.Environment;

/**
 * Configuração centralizada de providers de IA, extraída de ProcessingEngineSettings,
 * DocumentScraperSettings e DeduplicationSettings para eliminar duplicação.
 *
 * <p>Contém 12 campos de provider + métodos utilitários (isProviderConfigured, modelFor, etc.)
 * que antes estavam duplicados em múltiplos switches.</p>
 */
public record AiProviderSettings(
    String aiProviderChain,
    String openRouterApiKey,
    String openRouterBaseUrl,
    String openRouterModel,
    String geminiApiKey,
    String geminiModel,
    String ollamaBaseUrl,
    String ollamaModel,
    String azureOpenAiEndpoint,
    String azureOpenAiApiKey,
    String azureOpenAiDeployment,
    String azureOpenAiApiVersion
) {
    private static final String[] SUPPORTED_AI_PROVIDERS = { "gemini", "gemini_nativo", "openrouter", "ollama", "azure_openai" };

    public String[] getAiProviders() {
        return Arrays.stream(aiProviderChain.split(","))
            .map(String::trim)
            .filter(value -> !value.isBlank())
            .toArray(String[]::new);
    }

    public boolean isProviderConfigured(String provider) {
        return switch (provider.toLowerCase(Locale.ROOT)) {
            case "openrouter" -> isValidCredential(openRouterApiKey);
            case "gemini", "gemini_nativo" -> isValidCredential(geminiApiKey);
            case "ollama" -> !ollamaBaseUrl.isBlank() && !ollamaModel.isBlank();
            case "azure_openai" -> isValidCredential(azureOpenAiApiKey) && !azureOpenAiEndpoint.isBlank() && !azureOpenAiDeployment.isBlank() && !azureOpenAiApiVersion.isBlank();
            default -> false;
        };
    }

    private static boolean isValidCredential(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        var trimmed = value.trim();
        return !trimmed.equals("******") && !trimmed.equals("***") && !trimmed.equals("changeme") && trimmed.length() >= 10;
    }

    public String[] getUnsupportedAiProviders() {
        return Arrays.stream(getAiProviders())
            .filter(provider -> Arrays.stream(SUPPORTED_AI_PROVIDERS).noneMatch(supported -> supported.equalsIgnoreCase(provider)))
            .toArray(String[]::new);
    }

    public String[] getConfiguredAiProviders() {
        return Arrays.stream(getAiProviders()).filter(this::isProviderConfigured).toArray(String[]::new);
    }

    public String modelFor(String provider) {
        return switch (provider.toLowerCase(Locale.ROOT)) {
            case "openrouter" -> openRouterModel;
            case "gemini", "gemini_nativo" -> geminiModel;
            case "ollama" -> ollamaModel;
            case "azure_openai" -> azureOpenAiDeployment;
            default -> provider;
        };
    }

    public static ProviderIA providerEnum(String provider) {
        return switch (provider.toLowerCase(Locale.ROOT)) {
            case "openrouter" -> ProviderIA.OPEN_ROUTER;
            case "gemini", "gemini_nativo" -> ProviderIA.GEMINI_NATIVO;
            case "ollama" -> ProviderIA.OLLAMA;
            case "azure_openai" -> ProviderIA.AZURE_OPENAI;
            default -> ProviderIA.OLLAMA;
        };
    }

    public static void validateSupported(String provider) {
        switch (provider.toLowerCase(Locale.ROOT)) {
            case "openrouter", "gemini", "gemini_nativo", "ollama", "azure_openai" -> { }
            default -> throw new IllegalStateException("Provider IA nao suportado: " + provider);
        }
    }

    /**
     * Factory a partir do Spring Environment com prefixo customizável para o módulo.
     */
    public static AiProviderSettings fromEnvironment(Environment environment, String prefix) {
        return new AiProviderSettings(
            EnvReaders.read(environment, prefix + ".ai-provider-chain", "AI_PROVIDER_CHAIN", "openrouter,gemini_nativo,ollama,azure_openai"),
            EnvReaders.read(environment, prefix + ".open-router-api-key", "OPENROUTER_API_KEY", ""),
            EnvReaders.read(environment, prefix + ".open-router-base-url", "OPENROUTER_BASE_URL", "https://openrouter.ai/api/v1"),
            EnvReaders.read(environment, prefix + ".open-router-model", "OPENROUTER_MODEL", "nvidia/nemotron-nano-12b-v2-vl:free"),
            EnvReaders.read(environment, prefix + ".gemini-api-key", "GEMINI_API_KEY", ""),
            EnvReaders.read(environment, prefix + ".gemini-model", "GEMINI_MODEL", "gemini-3.1-flash-lite-preview"),
            EnvReaders.read(environment, prefix + ".ollama-base-url", "OLLAMA_BASE_URL", "http://localhost:11434"),
            EnvReaders.read(environment, prefix + ".ollama-model", "OLLAMA_MODEL", "llama3.2-vision:11b"),
            EnvReaders.read(environment, prefix + ".azure-openai-endpoint", "AZURE_OPENAI_ENDPOINT", ""),
            EnvReaders.read(environment, prefix + ".azure-openai-api-key", "AZURE_OPENAI_API_KEY", ""),
            EnvReaders.read(environment, prefix + ".azure-openai-deployment", "AZURE_OPENAI_DEPLOYMENT", ""),
            EnvReaders.read(environment, prefix + ".azure-openai-api-version", "AZURE_OPENAI_API_VERSION", "2024-10-21"));
    }
}
