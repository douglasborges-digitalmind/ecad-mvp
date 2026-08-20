package br.com.ecad.captacao.deduplicator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

import br.com.ecad.captacao.shared.domain.enums.ProviderIA;

record DeduplicationSettings(
    Path inputPath,
    boolean dryRun,
    boolean useAi,
    double heuristicDuplicateThreshold,
    double heuristicAiThreshold,
    List<String> blockingStrategies,
    List<String> providerChain,
    String openRouterApiKey,
    String openRouterModel,
    String openRouterBaseUrl,
    String geminiApiKey,
    String geminiModel,
    String geminiBaseUrl,
    String ollamaModel,
    String ollamaBaseUrl,
    String azureOpenAiEndpoint,
    String azureOpenAiApiKey,
    String azureOpenAiDeployment,
    String azureOpenAiApiVersion
) {
    private static final String DEFAULT_OPENROUTER_MODEL = "google/gemini-3.1-flash-lite-preview";
    private static final String DEFAULT_OPENROUTER_BASE_URL = "https://openrouter.ai/api/v1";
    private static final String DEFAULT_GEMINI_MODEL = "gemini-3.1-flash-lite-preview";
    private static final String DEFAULT_GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/v1beta";
    private static final String DEFAULT_OLLAMA_MODEL = "llama3:8b";
    private static final String DEFAULT_OLLAMA_BASE_URL = "http://localhost:11434";

    static DeduplicationSettings fromArgs(String[] args, Path baseDirectory) {
        String input = null;
        var dryRun = false;
        var useAi = true;
        Double duplicateThreshold = null;
        Double aiThreshold = null;
        List<String> blocking = null;
        for (var index = 0; index < args.length; index++) {
            switch (args[index]) {
                case "--input" -> input = args[++index];
                case "--dry-run" -> dryRun = true;
                case "--no-ai" -> useAi = false;
                case "--duplicate-threshold" -> duplicateThreshold = parseThreshold(args[++index], "--duplicate-threshold");
                case "--ai-threshold" -> aiThreshold = parseThreshold(args[++index], "--ai-threshold");
                case "--blocking-strategies" -> blocking = parseCsv(args[++index]);
                default -> {
                }
            }
        }
        return new DeduplicationSettings(
            resolveInputPath(input, baseDirectory),
            dryRun,
            useAi,
            duplicateThreshold == null ? parseThreshold(env("DEDUP_HEURISTIC_DUPLICATE_THRESHOLD", "0.82"), "DEDUP_HEURISTIC_DUPLICATE_THRESHOLD") : duplicateThreshold,
            aiThreshold == null ? parseThreshold(env("DEDUP_HEURISTIC_AI_THRESHOLD", "0.62"), "DEDUP_HEURISTIC_AI_THRESHOLD") : aiThreshold,
            blocking == null ? parseCsv(env("DEDUP_BLOCKING_STRATEGIES", "hash,url,title,title_city,title_date,city_date,title_city_date,local_city,promotor_date,cnpj")) : blocking,
            parseCsv(env("DEDUP_AI_PROVIDER_CHAIN", env("AI_PROVIDER_CHAIN", "openrouter,gemini_nativo,ollama,azure_openai"))),
            env("OPENROUTER_API_KEY", ""),
            env("OPENROUTER_MODEL", DEFAULT_OPENROUTER_MODEL),
            env("OPENROUTER_BASE_URL", DEFAULT_OPENROUTER_BASE_URL),
            env("GEMINI_API_KEY", ""),
            env("GEMINI_MODEL", DEFAULT_GEMINI_MODEL),
            env("GEMINI_BASE_URL", DEFAULT_GEMINI_BASE_URL),
            env("OLLAMA_MODEL", DEFAULT_OLLAMA_MODEL),
            env("OLLAMA_BASE_URL", DEFAULT_OLLAMA_BASE_URL),
            env("AZURE_OPENAI_ENDPOINT", ""),
            env("AZURE_OPENAI_API_KEY", ""),
            env("AZURE_OPENAI_DEPLOYMENT", ""),
            env("AZURE_OPENAI_API_VERSION", "2024-10-21"));
    }

    List<ProviderIA> availableProviders() {
        if (!useAi) {
            return List.of();
        }
        var providers = new LinkedHashSet<ProviderIA>();
        for (var providerName : providerChain) {
            switch (providerName.toLowerCase(java.util.Locale.ROOT)) {
                case "gemini", "gemini_nativo" -> {
                    if (!geminiApiKey.isBlank()) {
                        providers.add(ProviderIA.GEMINI_NATIVO);
                    }
                }
                case "openrouter" -> {
                    if (!openRouterApiKey.isBlank()) {
                        providers.add(ProviderIA.OPEN_ROUTER);
                    }
                }
                case "ollama" -> {
                    if (isOllamaAvailable()) {
                        providers.add(ProviderIA.OLLAMA);
                    }
                }
                case "azure_openai" -> {
                    if (!azureOpenAiEndpoint.isBlank() && !azureOpenAiApiKey.isBlank() && !azureOpenAiDeployment.isBlank()) {
                        providers.add(ProviderIA.AZURE_OPENAI);
                    }
                }
                default -> {
                }
            }
        }
        return List.copyOf(providers);
    }

    private boolean isOllamaAvailable() {
        return enabledFlag(System.getenv("DEDUP_OLLAMA_ENABLED"))
            || enabledFlag(System.getenv("OLLAMA_ENABLED"))
            || (!ollamaModel.equals(DEFAULT_OLLAMA_MODEL) || !ollamaBaseUrl.equalsIgnoreCase(DEFAULT_OLLAMA_BASE_URL));
    }

    private static Path resolveInputPath(String input, Path baseDirectory) {
        if (input != null && !input.isBlank()) {
            var path = Path.of(input);
            return path.isAbsolute() ? path.normalize() : baseDirectory.resolve(path).normalize();
        }
        var current = baseDirectory.toAbsolutePath().normalize();
        while (current != null) {
            var candidate = current.resolve(".localdev").resolve("data").resolve("eventos.json");
            if (Files.exists(candidate)) {
                return candidate;
            }
            if (Files.isDirectory(current.resolve("ECAD_Documents"))) {
                return candidate;
            }
            current = current.getParent();
        }
        return baseDirectory.resolve(".localdev").resolve("data").resolve("eventos.json").normalize();
    }

    private static List<String> parseCsv(String value) {
        return Arrays.stream((value == null ? "" : value).split(","))
            .map(String::trim)
            .filter(item -> !item.isBlank())
            .distinct()
            .toList();
    }

    private static double parseThreshold(String value, String source) {
        var parsed = Double.parseDouble(value);
        if (parsed < 0 || parsed > 1) {
            throw new IllegalArgumentException(source + " deve estar entre 0 e 1.");
        }
        return parsed;
    }

    private static String env(String name, String fallback) {
        var value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static boolean enabledFlag(String value) {
        return value != null && List.of("1", "true", "yes", "on").contains(value.toLowerCase(java.util.Locale.ROOT));
    }
}
