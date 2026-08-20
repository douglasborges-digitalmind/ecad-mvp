package br.com.ecad.captacao.documentscraper;

import java.net.URI;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.HexFormat;

final class ScraperUtilities {
    private ScraperUtilities() {
    }

    static String sha256(byte[] content) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    }

    static String stripMarkdownFences(String value) {
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

    static String slugify(String text) {
        var normalized = Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toLowerCase(java.util.Locale.ROOT)
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("^-+|-+$", "");
        return normalized.isBlank() ? "documento" : normalized;
    }

    static String fileNameFromUrl(String url, String fallback) {
        try {
            var path = URI.create(url).getPath();
            var lastSlash = path.lastIndexOf('/');
            var fileName = lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
            return fileName == null || fileName.isBlank() ? fallback : fileName;
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }
}
