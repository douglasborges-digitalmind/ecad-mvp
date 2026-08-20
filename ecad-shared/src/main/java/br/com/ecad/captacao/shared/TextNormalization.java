package br.com.ecad.captacao.shared;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

public final class TextNormalization {
    private static final Pattern MULTI_WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");

    private TextNormalization() {
    }

    public static String normalizeWhitespace(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return MULTI_WHITESPACE.matcher(value.trim()).replaceAll(" ");
    }

    public static String normalizeForComparison(String value) {
        var normalized = normalizeWhitespace(value);
        if (normalized == null || normalized.isBlank()) {
            return "";
        }

        var decomposed = Normalizer.normalize(normalized, Normalizer.Form.NFD);
        return DIACRITICS.matcher(decomposed).replaceAll("").toUpperCase(Locale.ROOT);
    }

    public static boolean equalsForComparison(String left, String right) {
        return normalizeForComparison(left).equals(normalizeForComparison(right));
    }
}
