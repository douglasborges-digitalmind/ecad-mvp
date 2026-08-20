package br.com.ecad.captacao.deduplicator;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import br.com.ecad.captacao.shared.JsonDefaults;
import br.com.ecad.captacao.shared.TextNormalization;
import br.com.ecad.captacao.shared.common.LruCache;
import br.com.ecad.captacao.shared.domain.entities.Evento;
import br.com.ecad.captacao.shared.domain.entities.Evidencia;
import br.com.ecad.captacao.shared.domain.enums.ProviderIA;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.stream.Collectors;

class EventoDeduplicationService {
    private static final Set<String> STOP_WORDS = Set.of("A", "AS", "O", "OS", "DA", "DAS", "DE", "DO", "DOS", "E", "EM", "NA", "NO", "NAS", "NOS", "COM", "PARA");
    private static final Set<String> OVERVIEW_MARKERS = Set.of("CALENDARIO", "PROGRAMACAO", "AGENDA", "CRONOGRAMA", "GRADE");
    private static final Pattern NON_TOKEN = Pattern.compile("[^A-Z0-9]+");
    private static final int POSTERIOR_MERGE_WINDOW_DAYS = 7;
    private static final int MAX_EVALUATION_CACHE_SIZE = 100_000;

    private final AiDuplicateDecider aiDuplicateDecider;
    private final DeduplicationSettings settings;
    private final Set<String> resolvedStrategies;
    private final LruCache<PairKey, PairEvaluation> evaluationCache;

    EventoDeduplicationService(AiDuplicateDecider aiDuplicateDecider, DeduplicationSettings settings) {
        this.aiDuplicateDecider = aiDuplicateDecider;
        this.settings = settings;
        this.resolvedStrategies = settings.blockingStrategies().stream()
            .map(s -> s.toLowerCase(java.util.Locale.ROOT))
            .collect(Collectors.toSet());
        this.evaluationCache = new LruCache<>(MAX_EVALUATION_CACHE_SIZE);
    }

    DeduplicationExecutionResult execute() throws Exception {
        if (!Files.exists(settings.inputPath())) {
            throw new IOException("Arquivo nao encontrado: " + settings.inputPath());
        }
        var originalJson = Files.readString(settings.inputPath());
        var eventos = JsonDefaults.objectMapper().readValue(originalJson, new TypeReference<List<Evento>>() { });
        var computation = deduplicate(eventos);
        Path backupPath = null;
        if (!settings.dryRun() && !computation.mergedGroups().isEmpty()) {
            backupPath = buildBackupPath(settings.inputPath());
            Files.writeString(backupPath, originalJson);
            Files.writeString(settings.inputPath(), JsonDefaults.objectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(computation.eventos()));
        }
        return new DeduplicationExecutionResult(settings.inputPath(), backupPath, settings.dryRun(), eventos.size(), computation.eventos().size(), computation.mergedGroups(), computation.stats(), computation.aiDecisionTraces());
    }

    DeduplicationComputationResult deduplicate(List<Evento> eventos) throws Exception {
        var items = eventos.stream().map(EventoDeduplicationService::cloneAndNormalize).toList();
        var parents = new int[items.size()];
        for (var index = 0; index < parents.length; index++) {
            parents[index] = index;
        }
        for (var pair : buildCandidatePairs(items)) {
            var evaluation = evaluatePair(items.get(pair.left()), items.get(pair.right()));
            if (evaluation.isDuplicate()) {
                union(parents, pair.left(), pair.right());
            }
        }

        var grouped = new HashMap<Integer, List<Evento>>();
        for (var index = 0; index < items.size(); index++) {
            grouped.computeIfAbsent(find(parents, index), ignored -> new ArrayList<>()).add(items.get(index));
        }

        var finalEvents = new ArrayList<Evento>();
        var mergedGroups = new ArrayList<MergedGroupResult>();
        for (var group : grouped.values()) {
            for (var validatedGroup : buildValidatedGroups(group, evaluationCache)) {
                if (validatedGroup.size() == 1) {
                    finalEvents.add(refresh(validatedGroup.getFirst()));
                    continue;
                }
                final Evento primary = validatedGroup.stream()
                    .min(Comparator.comparing(EventoDeduplicationService::chronologicalAnchorDate)
                        .thenComparing(evento -> nullSafe(evento.dataDescoberta()))
                        .thenComparing(evento -> evento.codigoEvento() == null ? "" : evento.codigoEvento()))
                    .orElseThrow();
                var primariesSecondary = new ArrayList<Evento>();
                for (var candidato : validatedGroup) {
                    if (candidato != primary) {
                        primariesSecondary.add(candidato);
                    }
                }
                primariesSecondary.sort(Comparator.comparingInt(EventoDeduplicationService::informationScore).reversed());
                var primaryCorrente = primary;
                for (var secondary : primariesSecondary) {
                    primaryCorrente = mergeInto(primaryCorrente, secondary);
                }
                primaryCorrente = refresh(primaryCorrente);
                finalEvents.add(primaryCorrente);
                var primaryFinal = primaryCorrente;
                var mergedCodes = primariesSecondary.stream()
                    .map(evento -> evento.codigoEvento())
                    .sorted()
                    .toList();
                mergedGroups.add(new MergedGroupResult(primaryFinal.codigoEvento(), mergedCodes));
            }
        }

        var ordered = finalEvents.stream()
            .sorted(Comparator.comparing(EventoDeduplicationService::chronologicalAnchorDate)
                .thenComparing(evento -> evento.titulo() == null ? "" : evento.titulo(), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(evento -> evento.codigoEvento() == null ? "" : evento.codigoEvento()))
            .toList();
        var aiTraces = evaluationCache.values().stream()
            .map(PairEvaluation::aiDecisionTrace)
            .filter(trace -> trace != null)
            .sorted(Comparator.comparing(AiDecisionTrace::codigoEventoA).thenComparing(AiDecisionTrace::codigoEventoB))
            .toList();
        var stats = new DeduplicationStats(
            evaluationCache.size(),
            (int) evaluationCache.values().stream().filter(PairEvaluation::isDuplicate).count(),
            (int) evaluationCache.values().stream().filter(evaluation -> !evaluation.isDuplicate()).count(),
            aiTraces.size(),
            (int) aiTraces.stream().filter(trace -> trace.duplicado() && trace.confianca() >= 0.65d).count());
        return new DeduplicationComputationResult(ordered, mergedGroups.stream().sorted(Comparator.comparing(MergedGroupResult::codigoPrincipal)).toList(), stats, aiTraces);
    }

    private PairEvaluation evaluatePair(Evento left, Evento right) throws Exception {
        var cacheKey = PairKey.of(left, right);
        var cached = evaluationCache.get(cacheKey);
        if (cached.isPresent()) {
            return cached.get();
        }
        var hasSameEvidence = hasSameEvidence(left, right);
        PairEvaluation result;
        if (hasExplicitLocationConflict(left, right) || isOverviewMismatch(left, right)) {
            result = PairEvaluation.notDuplicate(PairDecisionSource.REGRA, null);
        } else if (isSameEventInSameCityWithinPosteriorWindow(left, right)) {
            result = PairEvaluation.duplicate(0.94d, PairDecisionSource.REGRA, null);
        } else if (hasStrongExactMatch(left, right)) {
            result = PairEvaluation.duplicate(0.95d, PairDecisionSource.REGRA, null);
        } else if (hasSameEvidence && hasEvidenceCorroboration(left, right)) {
            result = PairEvaluation.duplicate(0.93d, PairDecisionSource.REGRA, null);
        } else if (!canBeCandidate(left, right, hasSameEvidence)) {
            result = PairEvaluation.notDuplicate(PairDecisionSource.REGRA, null);
        } else {
            result = evaluateHeuristicAndAi(left, right);
        }
        evaluationCache.put(cacheKey, result);
        return result;
    }

    private PairEvaluation evaluateHeuristicAndAi(Evento left, Evento right) throws Exception {
        var score = calculateHeuristicScore(left, right);
        if (score >= settings.heuristicDuplicateThreshold()) {
            return PairEvaluation.duplicate(score, PairDecisionSource.HEURISTICA, null);
        }
        if (score < settings.heuristicAiThreshold() || !aiDuplicateDecider.isEnabled()) {
            return PairEvaluation.notDuplicate(PairDecisionSource.HEURISTICA, null);
        }
        var decision = aiDuplicateDecider.decide(left, right, score);
        var trace = decision == null ? null : new AiDecisionTrace(left.codigoEvento(), right.codigoEvento(), decision.provider(), score, decision.duplicado(), decision.confianca(), decision.justificativa());
        return decision != null && decision.duplicado() && decision.confianca() >= 0.65d
            ? PairEvaluation.duplicate(score, PairDecisionSource.IA, trace)
            : PairEvaluation.notDuplicate(PairDecisionSource.IA, trace);
    }

    private List<Pair> buildCandidatePairs(List<Evento> items) {
        var buckets = new HashMap<String, List<Integer>>();
        var pairs = new HashSet<Pair>();
        for (var index = 0; index < items.size(); index++) {
            for (var key : blockingKeys(items.get(index))) {
                var bucket = buckets.computeIfAbsent(key, ignored -> new ArrayList<>());
                for (var other : bucket) {
                    pairs.add(new Pair(other, index));
                }
                bucket.add(index);
            }
        }
        return pairs.stream().sorted(Comparator.comparingInt(Pair::left).thenComparingInt(Pair::right)).toList();
    }

    private List<String> blockingKeys(Evento evento) {
        var keys = new HashSet<String>();
        for (var strategy : resolvedStrategies) {
            addBlockingKeys(keys, strategy, evento);
        }
        return List.copyOf(keys);
    }

    private static void addBlockingKeys(Set<String> keys, String strategy, Evento evento) {
        var titleKey = tokenFingerprint(evento.titulo(), 5);
        var cityKey = locationKey(evento.municipio(), evento.uf());
        var dateKey = dateKey(evento.dataInicio(), evento.dataTermino());
        var localKey = tokenFingerprint(evento.local(), 4);
        var promotorKey = tokenFingerprint(evento.promotorNome(), 4);
        var cnpjKey = digits(evento.promotorCnpj());
        switch (strategy.toLowerCase(java.util.Locale.ROOT)) {
            case "hash" -> evidenceList(evento).stream().map(evidence -> normalize(evidence.hashArquivo())).filter(value -> !value.isBlank()).forEach(value -> keys.add("HASH:" + value));
            case "url" -> evidenceList(evento).stream().map(evidence -> canonicalUrl(evidence.urlOrigem())).filter(value -> value != null && !value.isBlank()).forEach(value -> keys.add("URL:" + value));
            case "title" -> addIf(keys, "TITLE:", titleKey);
            case "title_city" -> addIf(keys, "TITLE_CITY:", join(titleKey, cityKey));
            case "title_date" -> addIf(keys, "TITLE_DATE:", join(titleKey, dateKey));
            case "city_date" -> addIf(keys, "CITY_DATE:", join(cityKey, dateKey));
            case "title_city_date" -> addIf(keys, "TITLE_CITY_DATE:", join(titleKey, cityKey, dateKey));
            case "local_city" -> addIf(keys, "LOCAL_CITY:", join(localKey, cityKey));
            case "promotor_date" -> addIf(keys, "PROMOTOR_DATE:", join(promotorKey, dateKey));
            case "cnpj" -> addIf(keys, "CNPJ:", cnpjKey);
            default -> {
            }
        }
    }

    private List<List<Evento>> buildValidatedGroups(List<Evento> items, LruCache<PairKey, PairEvaluation> cache) throws Exception {
        if (items.size() <= 1) {
            return List.of(items);
        }
        var groups = new ArrayList<List<Evento>>();
        var ordered = items.stream()
            .sorted(Comparator.comparing(EventoDeduplicationService::chronologicalAnchorDate)
                .thenComparing(evento -> nullSafe(evento.dataDescoberta()))
                .thenComparing(Comparator.comparingInt(EventoDeduplicationService::informationScore).reversed())
                .thenComparing(evento -> evento.codigoEvento() == null ? "" : evento.codigoEvento()))
            .toList();
        for (var item : ordered) {
            List<Evento> bestGroup = null;
            var bestScore = 0d;
            for (var group : groups) {
                var canJoin = true;
                var groupScore = Double.MAX_VALUE;
                for (var existing : group) {
                    var evaluation = evaluatePair(existing, item);
                    if (!evaluation.isDuplicate()) {
                        canJoin = false;
                        break;
                    }
                    groupScore = Math.min(groupScore, evaluation.score());
                }
                if (canJoin && groupScore > bestScore) {
                    bestGroup = group;
                    bestScore = groupScore;
                }
            }
            if (bestGroup == null) {
                var newGroup = new ArrayList<Evento>();
                newGroup.add(item);
                groups.add(newGroup);
            } else {
                bestGroup.add(item);
            }
        }
        return groups;
    }

    private static Evento cloneAndNormalize(Evento source) {
        return new Evento(
            source.id(),
            source.codigoEvento(),
            normalizeWhitespace(source.titulo()),
            source.dataInicio(),
            source.dataTermino(),
            normalizeWhitespace(source.local()),
            normalizeWhitespace(source.municipio()),
            normalizeWhitespace(source.uf()),
            normalizeWhitespace(source.unidadeEcad()),
            normalizeWhitespace(source.hora()),
            normalizeWhitespace(source.promotorCnpj()),
            normalizeWhitespace(source.promotorNome()),
            normalizeWhitespace(source.promotorContato()),
            list(source.interpretes()).stream().map(EventoDeduplicationService::normalizeWhitespace).filter(value -> value != null && !value.isBlank()).distinct().toList(),
            source.tipoMusica(),
            source.cobrancaIngresso(),
            source.valorIngresso(),
            source.capacidadePublico(),
            source.status(),
            source.statusSga(),
            source.nivelCompletude(),
            source.fontePrimaria(),
            source.dataDescoberta(),
            source.dataAtualizacao(),
            normalizeWhitespace(source.observacoesIa()),
            source.idFonteCaptacao(),
            reindexedEvidences(evidenceList(source))
        );
    }

    private static Evidencia copyEvidence(Evidencia source) {
        return new Evidencia(
            source.sequencia(),
            source.tipo(),
            normalizeWhitespace(source.urlOrigem()) == null ? "" : normalizeWhitespace(source.urlOrigem()),
            normalizeWhitespace(source.urlArmazenamentoInterno()) == null ? "" : normalizeWhitespace(source.urlArmazenamentoInterno()),
            source.dataCaptura(),
            normalizeWhitespace(source.hashArquivo()) == null ? "" : normalizeWhitespace(source.hashArquivo()),
            source.jsonBrutoUrlInterna(),
            source.evidenciaVisualUrlInterna(),
            source.observacoesIa()
        );
    }

    private static boolean canBeCandidate(Evento left, Evento right, boolean hasSameEvidence) {
        if (hasSameEvidence) {
            return true;
        }
        if (datesAlign(left, right)) {
            return titlesAreStrongMatch(left, right)
                || (haveSameMunicipioUf(left, right) && haveInterpreterOverlap(left, right))
                || (haveSameMunicipioUf(left, right) && locationsCompatible(left, right));
        }
        return datesAreAdjacentWithStrongContext(left, right);
    }

    private static boolean hasStrongExactMatch(Evento left, Evento right) {
        return equalsForComparison(left.titulo(), right.titulo()) && haveSameMunicipioUf(left, right) && datesAlign(left, right) && locationsCompatible(left, right);
    }

    private static boolean datesAlign(Evento left, Evento right) {
        if (left.dataInicio() == null || right.dataInicio() == null) {
            return true;
        }
        var leftEnd = left.dataTermino() == null ? left.dataInicio() : left.dataTermino();
        var rightEnd = right.dataTermino() == null ? right.dataInicio() : right.dataTermino();
        return !left.dataInicio().toLocalDate().isAfter(rightEnd.toLocalDate()) && !right.dataInicio().toLocalDate().isAfter(leftEnd.toLocalDate());
    }

    private static boolean datesAreAdjacentWithStrongContext(Evento left, Evento right) {
        return datesAreAdjacent(left, right) && haveSameMunicipioUf(left, right) && titlesAreVeryStrongMatch(left, right) && (hasStrongLocationMatch(left, right) || haveInterpreterOverlap(left, right));
    }

    private static boolean isSameEventInSameCityWithinPosteriorWindow(Evento left, Evento right) {
        return titlesAreStrongMatch(left, right) && haveSameMunicipioUf(left, right) && datesFallWithinPosteriorWindow(left, right);
    }

    private static boolean datesAreAdjacent(Evento left, Evento right) {
        if (left.dataInicio() == null || right.dataInicio() == null) {
            return false;
        }
        var leftEnd = left.dataTermino() == null ? left.dataInicio() : left.dataTermino();
        var rightEnd = right.dataTermino() == null ? right.dataInicio() : right.dataTermino();
        var leftGap = Math.abs(java.time.temporal.ChronoUnit.DAYS.between(left.dataInicio().toLocalDate(), rightEnd.toLocalDate()));
        var rightGap = Math.abs(java.time.temporal.ChronoUnit.DAYS.between(right.dataInicio().toLocalDate(), leftEnd.toLocalDate()));
        return Math.min(leftGap, rightGap) == 1;
    }

    private static boolean datesFallWithinPosteriorWindow(Evento left, Evento right) {
        var leftDate = chronologicalAnchorDate(left);
        var rightDate = chronologicalAnchorDate(right);
        if (leftDate.equals(OffsetDateTime.MAX) || rightDate.equals(OffsetDateTime.MAX)) {
            return false;
        }
        var older = leftDate.isBefore(rightDate) ? leftDate : rightDate;
        var newer = leftDate.isBefore(rightDate) ? rightDate : leftDate;
        return java.time.temporal.ChronoUnit.DAYS.between(older.toLocalDate(), newer.toLocalDate()) <= POSTERIOR_MERGE_WINDOW_DAYS;
    }

    private static boolean titlesAreStrongMatch(Evento left, Evento right) {
        var similarity = tokenSimilarity(left.titulo(), right.titulo());
        return similarity >= 0.78d || equalsForComparison(left.titulo(), right.titulo());
    }

    private static boolean titlesAreVeryStrongMatch(Evento left, Evento right) {
        var similarity = tokenSimilarity(left.titulo(), right.titulo());
        return similarity >= 0.9d || equalsForComparison(left.titulo(), right.titulo());
    }

    private static boolean haveSameMunicipioUf(Evento left, Evento right) {
        var municipioOk = isBlank(left.municipio()) || isBlank(right.municipio()) || equalsForComparison(left.municipio(), right.municipio());
        var ufOk = isBlank(left.uf()) || isBlank(right.uf()) || equalsForComparison(left.uf(), right.uf());
        return municipioOk && ufOk;
    }

    private static boolean locationsCompatible(Evento left, Evento right) {
        return isBlank(left.local()) || isBlank(right.local()) || tokenSimilarity(left.local(), right.local()) >= 0.65d;
    }

    private static boolean hasStrongLocationMatch(Evento left, Evento right) {
        return !isBlank(left.local()) && !isBlank(right.local()) && locationsCompatible(left, right);
    }

    private static boolean haveInterpreterOverlap(Evento left, Evento right) {
        var rightSet = new HashSet<String>();
        for (var interpreter : list(right.interpretes())) {
            rightSet.add(normalize(interpreter));
        }
        return list(left.interpretes()).stream().map(EventoDeduplicationService::normalize).anyMatch(rightSet::contains);
    }

    private static boolean hasExplicitLocationConflict(Evento left, Evento right) {
        var municipioConflict = !isBlank(left.municipio()) && !isBlank(right.municipio()) && !equalsForComparison(left.municipio(), right.municipio());
        var ufConflict = !isBlank(left.uf()) && !isBlank(right.uf()) && !equalsForComparison(left.uf(), right.uf());
        return municipioConflict || ufConflict;
    }

    private static boolean isOverviewMismatch(Evento left, Evento right) {
        return isOverview(left) != isOverview(right);
    }

    private static boolean isOverview(Evento evento) {
        if (isBlank(evento.titulo())) {
            return false;
        }
        var title = normalize(evento.titulo());
        return OVERVIEW_MARKERS.stream().anyMatch(title::contains) && (evento.dataInicio() == null || isBlank(evento.local()));
    }

    private static boolean hasSameEvidence(Evento left, Evento right) {
        for (var leftEvidence : evidenceList(left)) {
            var leftHash = normalize(leftEvidence.hashArquivo());
            var leftUrl = canonicalUrl(leftEvidence.urlOrigem());
            for (var rightEvidence : evidenceList(right)) {
                var rightHash = normalize(rightEvidence.hashArquivo());
                var rightUrl = canonicalUrl(rightEvidence.urlOrigem());
                if (!leftHash.isBlank() && leftHash.equals(rightHash)) {
                    return true;
                }
                if (leftUrl != null && rightUrl != null && leftUrl.equalsIgnoreCase(rightUrl)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasEvidenceCorroboration(Evento left, Evento right) {
        var corroboration = 0;
        if (titlesAreStrongMatch(left, right)) {
            corroboration++;
        }
        if (datesAlign(left, right) || datesAreAdjacentWithStrongContext(left, right)) {
            corroboration++;
        }
        if (haveSameMunicipioUf(left, right)) {
            corroboration++;
        }
        if (hasStrongLocationMatch(left, right)) {
            corroboration++;
        }
        if (haveInterpreterOverlap(left, right)) {
            corroboration++;
        }
        return corroboration >= 2;
    }

    private static double calculateHeuristicScore(Evento left, Evento right) {
        var score = 0d;
        var titleSimilarity = tokenSimilarity(left.titulo(), right.titulo());
        if (equalsForComparison(left.titulo(), right.titulo())) {
            score += 0.38d;
        } else if (titleSimilarity >= 0.85d) {
            score += 0.34d;
        } else if (titleSimilarity >= 0.7d) {
            score += 0.24d;
        }
        if (haveSameMunicipioUf(left, right)) {
            score += 0.18d;
        }
        if (left.dataInicio() != null && right.dataInicio() != null) {
            score += datesAlign(left, right) ? 0.22d : datesAreAdjacentWithStrongContext(left, right) ? 0.16d : 0d;
        } else if (left.dataInicio() != null || right.dataInicio() != null) {
            score += 0.04d;
        }
        var localSimilarity = tokenSimilarity(left.local(), right.local());
        if (equalsForComparison(left.local(), right.local()) && !isBlank(left.local())) {
            score += 0.16d;
        } else if (localSimilarity >= 0.65d) {
            score += 0.12d;
        } else if (isBlank(left.local()) || isBlank(right.local())) {
            score += 0.05d;
        }
        if (haveInterpreterOverlap(left, right)) {
            score += 0.18d;
        }
        if (!isBlank(left.promotorNome()) && equalsForComparison(left.promotorNome(), right.promotorNome())) {
            score += 0.08d;
        }
        return Math.min(score, 1d);
    }

    private static double tokenSimilarity(String left, String right) {
        var leftTokens = tokenize(left);
        var rightTokens = tokenize(right);
        if (leftTokens.isEmpty() || rightTokens.isEmpty()) {
            return 0d;
        }
        var intersection = new HashSet<>(leftTokens);
        intersection.retainAll(rightTokens);
        var union = new HashSet<>(leftTokens);
        union.addAll(rightTokens);
        return union.isEmpty() ? 0d : (double) intersection.size() / union.size();
    }

    private static Set<String> tokenize(String value) {
        if (isBlank(value)) {
            return Set.of();
        }
        var tokens = new HashSet<String>();
        for (var token : NON_TOKEN.split(normalize(value))) {
            if (token.length() > 1 && !STOP_WORDS.contains(token)) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    /**
     * Returns a new {@link Evento} that merges {@code source} into {@code target}.
     * <p>
     * Records are immutable, so this method produces a fresh instance instead of mutating
     * the target. Field selection logic preserves the original "choose the more
     * informative" heuristic for nullable text/enum fields.
     */
    private static Evento mergeInto(Evento target, Evento source) {
        var mergedTitle = chooseMoreInformative(target.titulo(), source.titulo(), true);
        var mergedLocal = chooseMoreInformative(target.local(), source.local(), false);
        var mergedMunicipio = chooseMoreInformative(target.municipio(), source.municipio(), false);
        var mergedUf = chooseMoreInformative(target.uf(), source.uf(), false);
        var mergedUnidadeEcad = chooseMoreInformative(target.unidadeEcad(), source.unidadeEcad(), false);
        var mergedHora = chooseMoreInformative(target.hora(), source.hora(), false);
        var mergedPromotorCnpj = chooseMoreInformative(target.promotorCnpj(), source.promotorCnpj(), false);
        var mergedPromotorNome = chooseMoreInformative(target.promotorNome(), source.promotorNome(), false);
        var mergedPromotorContato = chooseMoreInformative(target.promotorContato(), source.promotorContato(), false);
        var mergedObservacoes = mergeObservation(target.observacoesIa(), source.observacoesIa());

        var mergedInterpretes = new ArrayList<>(list(target.interpretes()));
        for (var interpreter : list(source.interpretes())) {
            if (mergedInterpretes.stream().noneMatch(existing -> equalsForComparison(existing, interpreter))) {
                mergedInterpretes.add(interpreter);
            }
        }

        var mergedEvidencias = new ArrayList<>(evidenceList(target));
        for (var evidence : evidenceList(source)) {
            if (mergedEvidencias.stream().noneMatch(existing -> sameEvidence(existing, evidence))) {
                mergedEvidencias.add(copyEvidence(evidence));
            }
        }

        var mergedDataDescoberta = target.dataDescoberta();
        if (source.dataDescoberta() != null && (mergedDataDescoberta == null || source.dataDescoberta().isBefore(mergedDataDescoberta))) {
            mergedDataDescoberta = source.dataDescoberta();
        }

        return new Evento(
            target.id(),
            target.codigoEvento(),
            mergedTitle,
            target.dataInicio() == null ? source.dataInicio() : target.dataInicio(),
            target.dataTermino() == null ? source.dataTermino() : target.dataTermino(),
            mergedLocal,
            mergedMunicipio,
            mergedUf,
            mergedUnidadeEcad,
            mergedHora,
            mergedPromotorCnpj,
            mergedPromotorNome,
            mergedPromotorContato,
            mergedInterpretes,
            target.tipoMusica() == null ? source.tipoMusica() : target.tipoMusica(),
            target.cobrancaIngresso() == null ? source.cobrancaIngresso() : target.cobrancaIngresso(),
            target.valorIngresso() == null ? source.valorIngresso() : target.valorIngresso(),
            target.capacidadePublico() == null ? source.capacidadePublico() : target.capacidadePublico(),
            target.status(),
            target.statusSga(),
            target.nivelCompletude(),
            target.fontePrimaria() == null ? source.fontePrimaria() : target.fontePrimaria(),
            mergedDataDescoberta,
            OffsetDateTime.now(),
            mergedObservacoes,
            target.idFonteCaptacao(),
            reindexedEvidences(mergedEvidencias)
        );
    }

    private static String chooseMoreInformative(String current, String incoming, boolean isTitle) {
        if (isBlank(current)) {
            return incoming;
        }
        if (isBlank(incoming)) {
            return current;
        }
        if (equalsForComparison(current, incoming)) {
            return incoming.length() > current.length() ? incoming : current;
        }
        return stringQuality(incoming, isTitle) > stringQuality(current, isTitle) ? incoming : current;
    }

    private static int stringQuality(String value, boolean isTitle) {
        var score = tokenize(value).size() * 10 + value.length();
        if (isTitle && OVERVIEW_MARKERS.stream().anyMatch(normalize(value)::contains)) {
            score -= 40;
        }
        return score;
    }

    private static String mergeObservation(String current, String incoming) {
        if (isBlank(current)) {
            return incoming;
        }
        if (isBlank(incoming) || equalsForComparison(current, incoming)) {
            return current;
        }
        return current + " | " + incoming;
    }

    private static int informationScore(Evento evento) {
        return (isBlank(evento.titulo()) ? 0 : 5)
            + (evento.dataInicio() == null ? 0 : 5)
            + (evento.dataTermino() == null ? 0 : 2)
            + (isBlank(evento.local()) ? 0 : 4)
            + (isBlank(evento.municipio()) ? 0 : 3)
            + (isBlank(evento.uf()) ? 0 : 2)
            + (isBlank(evento.promotorNome()) ? 0 : 2)
            + list(evento.interpretes()).size()
            + evidenceList(evento).size();
    }

    private static OffsetDateTime chronologicalAnchorDate(Evento evento) {
        var date = evento.dataInicio() == null ? evento.dataTermino() : evento.dataInicio();
        return date == null ? OffsetDateTime.MAX : date;
    }

    /**
     * Returns a new {@link Evento} with its evidence list re-indexed and derived state
     * ({@code status}, {@code nivelCompletude}) recalculated. The original instance is
     * not mutated because {@link Evento} is an immutable record.
     */
    private static Evento refresh(Evento evento) {
        var reindexed = reindexedEvidences(evidenceList(evento));
        Evento withEvidences = evento;
        if (reindexed != evento.evidencias()) {
            withEvidences = new Evento(
                evento.id(), evento.codigoEvento(), evento.titulo(), evento.dataInicio(), evento.dataTermino(),
                evento.local(), evento.municipio(), evento.uf(), evento.unidadeEcad(), evento.hora(),
                evento.promotorCnpj(), evento.promotorNome(), evento.promotorContato(), evento.interpretes(),
                evento.tipoMusica(), evento.cobrancaIngresso(), evento.valorIngresso(), evento.capacidadePublico(),
                evento.status(), evento.statusSga(), evento.nivelCompletude(), evento.fontePrimaria(),
                evento.dataDescoberta(), evento.dataAtualizacao(), evento.observacoesIa(), evento.idFonteCaptacao(),
                reindexed);
        }
        return withEvidences.comStatusAtualizado(null).comNivelCompletudeAtualizado();
    }

    /**
     * Returns a new list of {@link Evidencia} where the {@code sequencia} field is the
     * 1-based position in the returned list. Records are immutable, so this builds a
     * fresh list of new instances rather than mutating the input.
     */
    private static List<Evidencia> reindexedEvidences(List<Evidencia> source) {
        var result = new ArrayList<Evidencia>(source.size());
        for (var index = 0; index < source.size(); index++) {
            var evidence = source.get(index);
            var desiredSequencia = index + 1;
            if (evidence.sequencia() == desiredSequencia) {
                result.add(evidence);
            } else {
                result.add(new Evidencia(
                    desiredSequencia,
                    evidence.tipo(),
                    evidence.urlOrigem(),
                    evidence.urlArmazenamentoInterno(),
                    evidence.dataCaptura(),
                    evidence.hashArquivo(),
                    evidence.jsonBrutoUrlInterna(),
                    evidence.evidenciaVisualUrlInterna(),
                    evidence.observacoesIa()));
            }
        }
        return result;
    }

    private static Path buildBackupPath(Path inputPath) {
        var fileName = inputPath.getFileName().toString();
        var dot = fileName.lastIndexOf('.');
        var base = dot < 0 ? fileName : fileName.substring(0, dot);
        var extension = dot < 0 ? "" : fileName.substring(dot);
        return inputPath.resolveSibling(base + ".backup." + DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(java.time.LocalDateTime.now()) + extension);
    }

    private static int find(int[] parents, int index) {
        while (parents[index] != index) {
            parents[index] = parents[parents[index]];
            index = parents[index];
        }
        return index;
    }

    private static void union(int[] parents, int left, int right) {
        var leftRoot = find(parents, left);
        var rightRoot = find(parents, right);
        if (leftRoot != rightRoot) {
            parents[rightRoot] = leftRoot;
        }
    }

    private static String tokenFingerprint(String value, int maxTokens) {
        var tokens = tokenize(value).stream()
            .sorted(Comparator.comparingInt(String::length).reversed().thenComparing(Comparator.naturalOrder()))
            .limit(maxTokens)
            .sorted()
            .toList();
        return tokens.isEmpty() ? null : String.join("|", tokens);
    }

    private static String locationKey(String municipio, String uf) {
        var normalizedMunicipio = normalize(municipio);
        var normalizedUf = normalize(uf);
        return normalizedMunicipio.isBlank() && normalizedUf.isBlank() ? null : normalizedMunicipio + "|" + normalizedUf;
    }

    private static String dateKey(OffsetDateTime dataInicio, OffsetDateTime dataTermino) {
        if (dataInicio == null && dataTermino == null) {
            return null;
        }
        var start = java.util.Objects.requireNonNullElse(dataInicio, dataTermino);
        var end = java.util.Objects.requireNonNullElse(dataTermino, dataInicio);
        return DateTimeFormatter.BASIC_ISO_DATE.format(start.toLocalDate()) + "-" + DateTimeFormatter.BASIC_ISO_DATE.format(end.toLocalDate());
    }

    private static String canonicalUrl(String value) {
        if (isBlank(value)) {
            return null;
        }
        try {
            var uri = URI.create(value);
            var clean = new URI(uri.getScheme(), uri.getAuthority() == null ? null : uri.getAuthority().toLowerCase(java.util.Locale.ROOT), uri.getPath(), null, null).toString();
            return clean.replaceAll("/+$", "");
        } catch (Exception ex) {
            return normalize(value);
        }
    }

    private static boolean sameEvidence(Evidencia left, Evidencia right) {
        var leftHash = normalize(left.hashArquivo());
        var rightHash = normalize(right.hashArquivo());
        if (!leftHash.isBlank() && leftHash.equals(rightHash)) {
            return true;
        }
        var leftUrl = canonicalUrl(left.urlOrigem());
        var rightUrl = canonicalUrl(right.urlOrigem());
        return leftUrl != null && leftUrl.equalsIgnoreCase(rightUrl);
    }

    private static void addIf(Set<String> keys, String prefix, String value) {
        if (value != null && !value.isBlank()) {
            keys.add(prefix + value);
        }
    }

    private static String join(String... values) {
        if (Arrays.stream(values).anyMatch(value -> value == null || value.isBlank())) {
            return null;
        }
        return String.join("|", values);
    }

    private static String digits(String value) {
        return value == null ? "" : value.replaceAll("[^0-9]", "");
    }

    private static String normalizeWhitespace(String value) {
        return TextNormalization.normalizeWhitespace(value);
    }

    private static String normalize(String value) {
        return TextNormalization.normalizeForComparison(value);
    }

    private static boolean equalsForComparison(String left, String right) {
        return TextNormalization.equalsForComparison(left, right);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static List<String> list(List<String> values) {
        return values == null ? List.of() : values;
    }

    private static List<Evidencia> evidenceList(Evento evento) {
        return evento.evidencias() == null ? List.of() : evento.evidencias();
    }

    private static OffsetDateTime nullSafe(OffsetDateTime date) {
        return date == null ? OffsetDateTime.MAX : date;
    }

    record DeduplicationComputationResult(List<Evento> eventos, List<MergedGroupResult> mergedGroups, DeduplicationStats stats, List<AiDecisionTrace> aiDecisionTraces) {
    }

    record DeduplicationExecutionResult(Path inputPath, Path backupPath, boolean dryRun, int totalRead, int totalWritten, List<MergedGroupResult> mergedGroups, DeduplicationStats stats, List<AiDecisionTrace> aiDecisionTraces) {
    }

    record MergedGroupResult(String codigoPrincipal, List<String> codigosMesclados) {
    }

    record DeduplicationStats(int totalPairsEvaluated, int duplicatePairs, int rejectedPairs, int aiPairsEvaluated, int aiPairsAccepted) {
    }

    record AiDecisionTrace(String codigoEventoA, String codigoEventoB, ProviderIA provider, double heuristicScore, boolean duplicado, double confianca, String justificativa) {
    }

    enum PairDecisionSource {
        REGRA,
        HEURISTICA,
        IA
    }

    private record Pair(int left, int right) {
    }

    private record PairKey(UUID leftId, UUID rightId) {
        private static PairKey of(Evento left, Evento right) {
            return left.id().compareTo(right.id()) <= 0 ? new PairKey(left.id(), right.id()) : new PairKey(right.id(), left.id());
        }
    }

    private record PairEvaluation(boolean isDuplicate, double score, PairDecisionSource source, AiDecisionTrace aiDecisionTrace) {
        private static PairEvaluation notDuplicate(PairDecisionSource source, AiDecisionTrace trace) {
            return new PairEvaluation(false, 0d, source, trace);
        }

        private static PairEvaluation duplicate(double score, PairDecisionSource source, AiDecisionTrace trace) {
            return new PairEvaluation(true, score, source, trace);
        }
    }
}
