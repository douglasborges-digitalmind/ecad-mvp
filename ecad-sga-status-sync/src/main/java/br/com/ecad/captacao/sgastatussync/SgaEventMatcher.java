package br.com.ecad.captacao.sgastatussync;

import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Pattern;

import br.com.ecad.captacao.shared.TextNormalization;
import org.springframework.stereotype.Service;

@Service
class SgaEventMatcher {
    private static final int MAX_CANDIDATES_EVALUATE = 5;
    private static final int DEFAULT_TITLE_THRESHOLD = 85;
    private static final int DEFAULT_TITLE_THRESHOLD_NO_COD = 92;
    private static final int MUNICIPIO_THRESHOLD = 75;
    private static final int MUNICIPIO_THRESHOLD_NO_COD = 85;
    private static final Pattern WORD_PATTERN = Pattern.compile("[A-Z0-9]+");

    SgaMatchResult findBestValidMatch(SgaEventQuery query, List<SgaShowCandidate> candidates) {
        var titleThreshold = query.codMunicipio() == null ? DEFAULT_TITLE_THRESHOLD_NO_COD : DEFAULT_TITLE_THRESHOLD;
        var municipioThreshold = query.codMunicipio() == null ? MUNICIPIO_THRESHOLD_NO_COD : MUNICIPIO_THRESHOLD;
        if (candidates.isEmpty()) {
            return emptyResult(titleThreshold, municipioThreshold, query.codMunicipio() == null);
        }
        var ranked = candidates.stream()
            .map(candidate -> new RankedCandidate(candidate, candidates.indexOf(candidate), tokenSetRatio(query.tituloEvento(), candidate.titulo())))
            .sorted(Comparator.comparingDouble(RankedCandidate::score).reversed())
            .limit(MAX_CANDIDATES_EVALUATE)
            .toList();
        if (ranked.isEmpty()) {
            return emptyResult(titleThreshold, municipioThreshold, query.codMunicipio() == null);
        }
        var top1Score = ranked.getFirst().score();
        var hadPromisingCandidates = false;
        var candidatesEvaluated = 0;
        var top1Rejected = false;
        var top1RejectedReason = "";

        for (var index = 0; index < ranked.size(); index++) {
            candidatesEvaluated++;
            var position = index + 1;
            var item = ranked.get(index);
            var titleScore = item.score();
            var candidate = item.candidate();
            if (titleScore < titleThreshold) {
                if (position == 1) {
                    top1Rejected = true;
                    top1RejectedReason = "titulo";
                }
                continue;
            }
            hadPromisingCandidates = true;
            if (candidate.dataPrevista() == null || !candidate.dataPrevista().equals(query.dataRealizacao())) {
                if (position == 1) {
                    top1Rejected = true;
                    top1RejectedReason = "data";
                }
                continue;
            }
            var municipioScore = tokenSetRatio(query.municipio(), candidate.municipio());
            if (municipioScore < municipioThreshold) {
                if (position == 1) {
                    top1Rejected = true;
                    top1RejectedReason = "municipio";
                }
                continue;
            }
            var result = emptyResult(titleThreshold, municipioThreshold, query.codMunicipio() == null);
            result.found = true;
            result.titleScore = titleScore;
            result.municipioScore = municipioScore;
            result.candidatePosition = position;
            result.candidatesEvaluated = candidatesEvaluated;
            result.top1Score = top1Score;
            result.top1Rejected = top1Rejected || position > 1;
            result.top1RejectedReason = top1RejectedReason;
            return result;
        }
        var result = emptyResult(titleThreshold, municipioThreshold, query.codMunicipio() == null);
        result.candidatesEvaluated = candidatesEvaluated;
        result.top1Score = top1Score;
        result.top1Rejected = top1Rejected;
        result.top1RejectedReason = top1RejectedReason;
        result.hadPromisingCandidates = hadPromisingCandidates;
        return result;
    }

    static double tokenSetRatio(String left, String right) {
        var leftTokens = tokenize(left);
        var rightTokens = tokenize(right);
        if (leftTokens.isEmpty() && rightTokens.isEmpty()) {
            return 100;
        }
        if (leftTokens.isEmpty() || rightTokens.isEmpty()) {
            return 0;
        }
        var intersection = new LinkedHashSet<>(leftTokens);
        intersection.retainAll(rightTokens);
        var leftDiff = new LinkedHashSet<>(leftTokens);
        leftDiff.removeAll(intersection);
        var rightDiff = new LinkedHashSet<>(rightTokens);
        rightDiff.removeAll(intersection);
        var sortedIntersection = joinSorted(intersection);
        var leftCombined = joinNonEmpty(sortedIntersection, joinSorted(leftDiff));
        var rightCombined = joinNonEmpty(sortedIntersection, joinSorted(rightDiff));
        var combined = joinNonEmpty(leftCombined, rightCombined);
        return Arrays.stream(new double[] {
            ratio(sortedIntersection, leftCombined),
            ratio(sortedIntersection, rightCombined),
            ratio(leftCombined, rightCombined),
            ratio(TextNormalization.normalizeForComparison(left), TextNormalization.normalizeForComparison(right)),
            ratio(sortedIntersection, combined)
        }).max().orElse(0);
    }

    private static LinkedHashSet<String> tokenize(String value) {
        var normalized = TextNormalization.normalizeForComparison(value);
        var matcher = WORD_PATTERN.matcher(normalized);
        var tokens = new LinkedHashSet<String>();
        while (matcher.find()) {
            tokens.add(matcher.group());
        }
        return tokens;
    }

    private static double ratio(String left, String right) {
        if ((left == null || left.isBlank()) && (right == null || right.isBlank())) {
            return 100;
        }
        if (left == null || left.isBlank() || right == null || right.isBlank()) {
            return 0;
        }
        var distance = levenshteinDistance(left, right);
        var maxLength = Math.max(left.length(), right.length());
        return maxLength == 0 ? 100 : (1.0 - (double) distance / maxLength) * 100.0;
    }

    private static int levenshteinDistance(String left, String right) {
        var previous = new int[right.length() + 1];
        var current = new int[right.length() + 1];
        for (var index = 0; index <= right.length(); index++) {
            previous[index] = index;
        }
        for (var leftIndex = 1; leftIndex <= left.length(); leftIndex++) {
            current[0] = leftIndex;
            for (var rightIndex = 1; rightIndex <= right.length(); rightIndex++) {
                var cost = left.charAt(leftIndex - 1) == right.charAt(rightIndex - 1) ? 0 : 1;
                current[rightIndex] = Math.min(Math.min(current[rightIndex - 1] + 1, previous[rightIndex] + 1), previous[rightIndex - 1] + cost);
            }
            var tmp = previous;
            previous = current;
            current = tmp;
        }
        return previous[right.length()];
    }

    private static String joinSorted(LinkedHashSet<String> values) {
        return values.stream().sorted().collect(java.util.stream.Collectors.joining(" "));
    }

    private static String joinNonEmpty(String... values) {
        return Arrays.stream(values).filter(value -> value != null && !value.isBlank()).collect(java.util.stream.Collectors.joining(" "));
    }

    private static SgaMatchResult emptyResult(int titleThreshold, int municipioThreshold, boolean thresholdElevated) {
        var result = new SgaMatchResult();
        result.titleThresholdUsed = titleThreshold;
        result.municipioThresholdUsed = municipioThreshold;
        result.thresholdElevated = thresholdElevated;
        return result;
    }

    private record RankedCandidate(SgaShowCandidate candidate, int index, double score) {
    }
}
