package br.com.ecad.captacao.processingengine;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import br.com.ecad.captacao.shared.infrastructure.blob.BlobDownload;
import br.com.ecad.captacao.shared.infrastructure.blob.BlobStorageService;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
@Service
class DocumentContentReader {
    private static final Pattern PDF_TEXT_TOKEN = Pattern.compile("\\(([^()]{8,})\\)\\s*Tj");

    private final BlobStorageService blobStorage;

    DocumentContentReader(BlobStorageService blobStorage) {
        this.blobStorage = blobStorage;
    }

    DocumentContentPayload read(String blobUrl) throws Exception {
        var download = blobStorage.download(blobUrl);
        var contentType = download.contentType() == null ? "application/octet-stream" : download.contentType();
        var rawContent = download.content();
        var isPdf = contentType.equalsIgnoreCase("application/pdf") || blobUrl.toLowerCase(java.util.Locale.ROOT).endsWith(".pdf");
        if (isPdf) {
            var text = extractSimplePdfText(rawContent);
            var usable = hasUsableText(text);
            return new DocumentContentPayload(rawContent, contentType, usable ? null : rawContent, contentType, usable ? text : "", true, usable);
        }

        var text = new String(rawContent, StandardCharsets.UTF_8);
        var textual = contentType.startsWith("text/") || contentType.contains("json") || hasUsableText(text);
        return new DocumentContentPayload(rawContent, contentType, textual ? null : rawContent, contentType, textual ? text : "", false, textual);
    }

    private static String extractSimplePdfText(byte[] rawContent) {
        try (var document = Loader.loadPDF(rawContent)) {
            return new PDFTextStripper().getText(document);
        } catch (java.io.IOException ignored) {
        }

        var raw = new String(rawContent, StandardCharsets.ISO_8859_1);
        var matcher = PDF_TEXT_TOKEN.matcher(raw);
        var result = new StringBuilder();
        while (matcher.find()) {
            if (!result.isEmpty()) {
                result.append('\n');
            }
            result.append(unescapePdfString(matcher.group(1)));
        }
        return result.toString();
    }

    private static String unescapePdfString(String value) {
        return value.replace("\\(", "(").replace("\\)", ")").replace("\\\\", "\\");
    }

    private static boolean hasUsableText(String text) {
        if (text == null) {
            return false;
        }
        var letters = text.chars().filter(Character::isLetter).count();
        return letters >= 30;
    }
}
