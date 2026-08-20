package br.com.ecad.captacao.processingengine;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.HexFormat;
import java.util.regex.Pattern;

import br.com.ecad.captacao.shared.JsonDefaults;
import br.com.ecad.captacao.shared.TextNormalization;
import br.com.ecad.captacao.shared.contracts.DocumentoCapturado;
import br.com.ecad.captacao.shared.domain.entities.CriterioExtracao;
import br.com.ecad.captacao.shared.domain.entities.MetricaExecucaoIA;
import br.com.ecad.captacao.shared.domain.enums.ComponenteIA;
import br.com.ecad.captacao.shared.domain.enums.TipoEvidencia;
import br.com.ecad.captacao.shared.domain.enums.TipoOperacaoIA;
import br.com.ecad.captacao.shared.prompts.ExtractionPrompts;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.stereotype.Service;

@Service
class ExtractionService {
    private static final int MAX_PROMPT_CONTENT_CHARS = 50_000;
    private static final int MAX_CONTEXT_BLOCK_CHARS = 500;
    private static final String PROMPT_VERSION = "v4-forensic-proof-first";

    private static final Pattern MULTI_BREAK_PATTERN = Pattern.compile("(?:\\r?\\n){2,}");
    private static final Pattern DATE_LIKE_PATTERN = Pattern.compile(
        "\\b(?:\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4}|\\d{4}-\\d{2}-\\d{2}|\\d{1,2}\\s+de\\s+(?:janeiro|fevereiro|março|abril|maio|junho|julho|agosto|setembro|outubro|novembro|dezembro)\\s+de\\s+\\d{2,4})\\b",
        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern TIME_LIKE_PATTERN = Pattern.compile("\\b\\d{1,2}:\\d{2}\\b");
    private static final Pattern CURRENCY_PATTERN = Pattern.compile("R\\$\\s*\\d", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final List<String> COMMON_PROMPT_KEYWORDS = List.of(
        "show",
        "apresentação",
        "festival",
        "evento",
        "cantor",
        "banda",
        "dj",
        "local",
        "município",
        "prefeitura");

    private final AiProviderChain aiProviderChain;
    private final DocumentContentReader contentReader;
    private final ExtractionResultCache cache;

    ExtractionService(AiProviderChain aiProviderChain, DocumentContentReader contentReader, ExtractionResultCache cache) {
        this.aiProviderChain = aiProviderChain;
        this.contentReader = contentReader;
        this.cache = cache;
    }

    ExtractionExecutionResult extract(DocumentoCapturado documento, CriterioExtracao criterio) throws Exception {
        var cacheKey = buildCacheKey(documento, criterio);
        var cached = cache.get(cacheKey);
        if (cached.isPresent()) {
            return cached.get();
        }

        var payload = contentReader.read(documento.urlStagingInterno());
        var prompt = buildPrompt(documento, criterio, payload);
        var execution = aiProviderChain.processar(prompt, payload.mediaBytes(), payload.mimeType(), documento.tipo(), documento.idFonteCaptacao());
        var metricas = execution.metricas() == null ? new ArrayList<MetricaExecucaoIA>() : new ArrayList<>(execution.metricas());
        if (metricas.isEmpty()) {
            metricas.add(toMetric(documento, prompt, execution.response()));
        }

        try {
            var rawContent = execution.response().content();
            var cleanContent = stripMarkdownFences(rawContent);
            var jsonContent = extractJsonObject(cleanContent);
            var result = JsonDefaults.objectMapper().readValue(jsonContent, ExtractionResult.class);
            var status = result.eventoIdentificado ? ExtractionExecutionStatus.SUCCESS : ExtractionExecutionStatus.NO_EVENT;
            var extraction = new ExtractionExecutionResult(result, metricas, status, null);
            cache.save(cacheKey, extraction);
            return extraction;
        } catch (JsonProcessingException ex) {
            return new ExtractionExecutionResult(new ExtractionResult(), metricas, ExtractionExecutionStatus.INVALID_AI_RESPONSE,
                "Falha ao interpretar a resposta JSON da IA.");
        }
    }

    /**
     * Remove cercas de markdown (```json ... ```) da resposta da IA, se presentes.
     * Alguns provedores (ex.: OpenRouter) podem retornar JSON envolto em blocos de código markdown.
     */
    private static String stripMarkdownFences(String value) {
        if (value == null) {
            return "";
        }
        var trimmed = value.trim();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }
        var firstNewLine = trimmed.indexOf('\n');
        var withoutOpening = firstNewLine >= 0 ? trimmed.substring(firstNewLine + 1) : trimmed.substring(3);
        var closing = withoutOpening.lastIndexOf("```");
        return closing >= 0 ? withoutOpening.substring(0, closing).trim() : withoutOpening.trim();
    }

    /**
     * Extrai o primeiro objeto JSON válido da resposta, ignorando texto antes/depois.
     * Modelos de IA podem adicionar comentários ou texto explicativo ao redor do JSON.
     */
    private static String extractJsonObject(String content) {
        if (content == null || content.isBlank()) {
            return content;
        }
        var trimmed = content.trim();
        var firstBrace = trimmed.indexOf('{');
        if (firstBrace < 0) {
            return trimmed;
        }
        var lastBrace = trimmed.lastIndexOf('}');
        if (lastBrace <= firstBrace) {
            return trimmed;
        }
        return trimmed.substring(firstBrace, lastBrace + 1);
    }

    private static MetricaExecucaoIA toMetric(DocumentoCapturado documento, String prompt, AiResponse response) {
        var metrica = new MetricaExecucaoIA();
        metrica.idExecucao = UUID.randomUUID();
        metrica.componente = ComponenteIA.PROCESSING_ENGINE;
        metrica.tipoOperacao = TipoOperacaoIA.EXTRACAO_SEMANTICA;
        metrica.tipoDocumento = documento.tipo();
        metrica.modeloUtilizado = response.model();
        metrica.provider = response.provider();
        metrica.tokensInput = response.tokensInput();
        metrica.tokensOutput = response.tokensOutput();
        metrica.custoUsd = response.costUsd();
        metrica.tamanhoInputChars = prompt.length();
        metrica.idFonteCaptacao = documento.idFonteCaptacao();
        metrica.sucesso = true;
        metrica.timestamp = OffsetDateTime.now();
        return metrica;
    }

    private static String buildPrompt(DocumentoCapturado documento, CriterioExtracao criterio, DocumentContentPayload payload) {
        var defaultGuidance = ExtractionPrompts.getGuidanceFor(documento.tipo());
        var sections = new ArrayList<>(ExtractionPrompts.getCoreSections(documento.tipo()));

        if (criterio.instrucoesExtracaoIa != null
            && !criterio.instrucoesExtracaoIa.isBlank()
            && !criterio.instrucoesExtracaoIa.trim().equals(defaultGuidance)) {
            sections.add("CRITÉRIO ESPECÍFICO:\n" + criterio.instrucoesExtracaoIa.trim());
        }

        var metadata = formatMetadata(documento.metadados());
        var externalContext = formatExternalContext(documento.instrucoesCaptura(), metadata);
        if (externalContext != null && !externalContext.isBlank()) {
            sections.add(externalContext);
        }

        var selectedContent = selectRelevantContent(documento.tipo(), payload.textContent());
        if (!selectedContent.isBlank()) {
            sections.add("MATERIAL PROBATÓRIO DO DOCUMENTO:\n" + selectedContent);
        }

        sections.add("FORMATO DE SAÍDA OBRIGATÓRIO:\n" + ExtractionPrompts.OUTPUT_SCHEMA);
        return String.join(System.lineSeparator() + System.lineSeparator(), sections);
    }

    private static String formatExternalContext(String instrucoesCaptura, String metadata) {
        var parts = new ArrayList<String>();
        if (instrucoesCaptura != null && !instrucoesCaptura.isBlank()) {
            parts.add("CONTEXTO DE CAPTURA:\n" + instrucoesCaptura.trim());
        }
        if (metadata != null && !metadata.isBlank()) {
            parts.add("METADADOS NÃO PROBATÓRIOS:\n" + metadata);
        }
        if (parts.isEmpty()) {
            return null;
        }
        return "CONTEXTO EXTERNO NÃO PROBATÓRIO:\n" + String.join(System.lineSeparator() + System.lineSeparator(), parts);
    }

    private static String selectRelevantContent(TipoEvidencia tipoDocumento, String textContent) {
        var normalized = normalizePromptText(textContent);
        if (normalized.isBlank()) {
            return "";
        }

        if (normalized.length() <= MAX_PROMPT_CONTENT_CHARS) {
            return normalized;
        }

        var blocks = splitIntoBlocks(normalized);
        if (blocks.isEmpty()) {
            return normalized.substring(0, MAX_PROMPT_CONTENT_CHARS);
        }

        var scores = new ArrayList<BlockScore>(blocks.size());
        for (var index = 0; index < blocks.size(); index++) {
            scores.add(new BlockScore(index, scoreBlock(tipoDocumento, blocks.get(index))));
        }

        var headerBlock = blocks.getFirst().length() > 4_000 ? blocks.getFirst().substring(0, 4_000) : blocks.getFirst();
        var scoredCandidates = scores.stream()
            .filter(score -> score.index() != 0)
            .filter(score -> isRelevantCandidate(tipoDocumento, blocks.get(score.index()), score.score()))
            .sorted((left, right) -> {
                var byScore = Integer.compare(right.score(), left.score());
                return byScore != 0 ? byScore : Integer.compare(left.index(), right.index());
            })
            .toList();

        var candidateSet = new HashSet<Integer>();
        candidateSet.add(0);
        for (var candidate : scoredCandidates) {
            candidateSet.add(candidate.index());
            if (candidate.index() > 1) {
                candidateSet.add(candidate.index() - 1);
            }
            if (candidate.index() < blocks.size() - 1) {
                candidateSet.add(candidate.index() + 1);
            }
        }

        var prioritizedCandidates = scoredCandidates.stream()
            .map(BlockScore::index)
            .distinct()
            .toList();
        var contextualCandidates = candidateSet.stream()
            .filter(index -> index != 0 && !prioritizedCandidates.contains(index))
            .sorted()
            .toList();

        var builder = new StringBuilder(MAX_PROMPT_CONTENT_CHARS);
        builder.append(headerBlock);

        for (var index : prioritizedCandidates) {
            appendBlock(builder, blocks.get(index));
            if (builder.length() >= MAX_PROMPT_CONTENT_CHARS) {
                return builder.toString().trim();
            }
        }

        for (var index : contextualCandidates) {
            var contextBlock = getContextBlockSnippet(blocks.get(index), prioritizedCandidates.stream().anyMatch(scoredIndex -> scoredIndex > index));
            appendBlock(builder, contextBlock);
            if (builder.length() >= MAX_PROMPT_CONTENT_CHARS) {
                return builder.toString().trim();
            }
        }

        if (scoredCandidates.isEmpty()) {
            for (var index = 1; index < blocks.size() && builder.length() < MAX_PROMPT_CONTENT_CHARS; index++) {
                if (!candidateSet.contains(index)) {
                    appendBlock(builder, blocks.get(index));
                }
            }
        }

        return builder.toString().trim();
    }

    private static List<String> splitIntoBlocks(String content) {
        return MULTI_BREAK_PATTERN.splitAsStream(content)
            .map(String::trim)
            .filter(block -> !block.isEmpty())
            .toList();
    }

    private static String normalizePromptText(String textContent) {
        if (textContent == null || textContent.isBlank()) {
            return "";
        }

        var lines = textContent.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        var builder = new StringBuilder(textContent.length());
        var blankLinePending = false;

        for (var line : lines) {
            var normalized = TextNormalization.normalizeWhitespace(line);
            if (normalized == null || normalized.isBlank()) {
                blankLinePending = builder.length() > 0;
                continue;
            }

            if (blankLinePending) {
                builder.append(System.lineSeparator()).append(System.lineSeparator());
                blankLinePending = false;
            } else if (builder.length() > 0) {
                builder.append(System.lineSeparator());
            }

            builder.append(normalized);
        }

        return builder.toString().trim();
    }

    private static void appendBlock(StringBuilder builder, String block) {
        if (builder.length() > 0) {
            builder.append(System.lineSeparator()).append(System.lineSeparator());
        }

        var remaining = MAX_PROMPT_CONTENT_CHARS - builder.length();
        if (remaining <= 0) {
            return;
        }

        builder.append(block.length() <= remaining ? block : block.substring(0, remaining));
    }

    private static String getContextBlockSnippet(String block, boolean preferTail) {
        if (block.length() <= MAX_CONTEXT_BLOCK_CHARS) {
            return block;
        }
        return preferTail
            ? block.substring(block.length() - MAX_CONTEXT_BLOCK_CHARS)
            : block.substring(0, MAX_CONTEXT_BLOCK_CHARS);
    }

    private static int scoreBlock(TipoEvidencia tipoDocumento, String block) {
        var score = 0;

        for (var keyword : COMMON_PROMPT_KEYWORDS) {
            if (containsIgnoreCase(block, keyword)) {
                score += 8;
            }
        }
        for (var keyword : getTipoKeywords(tipoDocumento)) {
            if (containsIgnoreCase(block, keyword)) {
                score += 14;
            }
        }

        score += countMatches(DATE_LIKE_PATTERN, block) * 10;
        score += countMatches(TIME_LIKE_PATTERN, block) * 5;
        score += countMatches(CURRENCY_PATTERN, block) * 4;

        if (containsIgnoreCase(block, "cnpj")) {
            score += 4;
        }
        if (block.length() < 40) {
            score -= 4;
        }

        return score;
    }

    private static boolean isRelevantCandidate(TipoEvidencia tipoDocumento, String block, int score) {
        if (score <= 0) {
            return false;
        }
        var keywordHits = getKeywordHits(tipoDocumento, block);
        return keywordHits >= 2
            || DATE_LIKE_PATTERN.matcher(block).find()
            || TIME_LIKE_PATTERN.matcher(block).find()
            || CURRENCY_PATTERN.matcher(block).find();
    }

    private static int getKeywordHits(TipoEvidencia tipoDocumento, String block) {
        var keywordHits = 0;
        for (var keyword : COMMON_PROMPT_KEYWORDS) {
            if (containsIgnoreCase(block, keyword)) {
                keywordHits++;
            }
        }
        for (var keyword : getTipoKeywords(tipoDocumento)) {
            if (containsIgnoreCase(block, keyword)) {
                keywordHits++;
            }
        }
        return keywordHits;
    }

    private static List<String> getTipoKeywords(TipoEvidencia tipoDocumento) {
        return switch (tipoDocumento) {
            case CONTRATO_MUSICAL -> List.of("contrato", "cláusula", "cachê", "contratante", "show", "vigência", "prazo", "objeto", "artista", "apresentação");
        };
    }

    private static String buildCacheKey(DocumentoCapturado documento, CriterioExtracao criterio) throws Exception {
        var metadata = formatMetadata(documento.metadados());
        var source = String.join(
            "||",
            PROMPT_VERSION,
            safeTrim(documento.hashConteudo()),
            documento.tipo().name(),
            criterio.instrucoesExtracaoIa == null ? "" : criterio.instrucoesExtracaoIa.trim(),
            documento.instrucoesCaptura() == null ? "" : documento.instrucoesCaptura().trim(),
            metadata == null ? "" : metadata);
        var hash = MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }

    private static String formatMetadata(Map<String, String> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        var lines = metadata.entrySet().stream()
            .filter(entry -> entry.getKey() != null && !entry.getKey().isBlank())
            .filter(entry -> entry.getValue() != null && !entry.getValue().isBlank())
            .map(entry -> "- " + entry.getKey() + ": " + entry.getValue())
            .toList();
        return lines.isEmpty() ? null : String.join(System.lineSeparator(), lines);
    }

    private static int countMatches(Pattern pattern, String value) {
        var matcher = pattern.matcher(value);
        var count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private static boolean containsIgnoreCase(String text, String fragment) {
        return text.toLowerCase(Locale.ROOT).contains(fragment.toLowerCase(Locale.ROOT));
    }

    private static String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private record BlockScore(int index, int score) {
    }
}