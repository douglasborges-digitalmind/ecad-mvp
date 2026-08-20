package br.com.ecad.captacao.shared.referencedata;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;

import br.com.ecad.captacao.shared.TextNormalization;
import br.com.ecad.captacao.shared.domain.entities.MunicipioUnidade;

public final class MunicipioUnidadeReferenceCatalog {
    private static final String RESOURCE_PATH = "/ReferenceData/fontesPNCP.csv";
    private static final AtomicReference<List<MunicipioUnidade>> CACHE = new AtomicReference<>();

    private MunicipioUnidadeReferenceCatalog() {
    }

    public static List<MunicipioUnidade> getAll() {
        var cached = CACHE.get();
        if (cached != null) {
            return cached;
        }

        var loaded = loadEntries();
        CACHE.compareAndSet(null, loaded);
        return CACHE.get();
    }

    public static Optional<MunicipioUnidade> tryResolve(String municipio, String uf) {
        var normalizedMunicipio = TextNormalization.normalizeForComparison(municipio);
        if (normalizedMunicipio.isBlank()) {
            return Optional.empty();
        }

        var normalizedUf = TextNormalization.normalizeForComparison(uf);
        var candidates = getAll().stream()
            .filter(item -> TextNormalization.normalizeForComparison(item.municipio).equals(normalizedMunicipio))
            .toList();

        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        if (!normalizedUf.isBlank()) {
            return candidates.stream()
                .filter(item -> TextNormalization.normalizeForComparison(item.uf).equals(normalizedUf))
                .findFirst();
        }

        return candidates.size() == 1 ? Optional.of(candidates.getFirst()) : Optional.empty();
    }

    private static List<MunicipioUnidade> loadEntries() {
        try {
            return readLines(StandardCharsets.UTF_8).stream()
                .skip(1)
                .map(MunicipioUnidadeReferenceCatalog::parseLine)
                .flatMap(Optional::stream)
                .toList();
        } catch (CharacterCodingException ex) {
            try {
                return readLines(StandardCharsets.ISO_8859_1).stream()
                    .skip(1)
                    .map(MunicipioUnidadeReferenceCatalog::parseLine)
                    .flatMap(Optional::stream)
                    .toList();
            } catch (IOException ioException) {
                return List.of();
            }
        } catch (IOException ex) {
            return List.of();
        }
    }

    private static List<String> readLines(java.nio.charset.Charset charset) throws IOException {
        var stream = MunicipioUnidadeReferenceCatalog.class.getResourceAsStream(RESOURCE_PATH);
        if (stream == null) {
            return List.of();
        }

        var decoder = charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT);

        try (var reader = new BufferedReader(new InputStreamReader(stream, decoder))) {
            var lines = new ArrayList<String>();
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }

            return lines;
        }
    }

    private static Optional<MunicipioUnidade> parseLine(String line) {
        if (line == null || line.isBlank()) {
            return Optional.empty();
        }

        var parts = line.split(";", -1);
        if (parts.length < 6) {
            return Optional.empty();
        }

        var uf = normalizeCsvValue(parts[2]);
        var municipio = normalizeCsvValue(parts[1]);
        var unidade = normalizeCsvValue(parts[5]);
        if (uf.isBlank() || municipio.isBlank() || unidade.isBlank()) {
            return Optional.empty();
        }

        var result = new MunicipioUnidade();
        result.uf = uf.toUpperCase(Locale.ROOT);
        result.municipio = toPortugueseTitleCase(municipio);
        result.unidadeEcad = toPortugueseTitleCase(unidade);
        return Optional.of(result);
    }

    private static String normalizeCsvValue(String value) {
        var unquoted = value == null ? "" : value.trim().replaceAll("^\"|\"$", "");
        var normalized = TextNormalization.normalizeWhitespace(unquoted);
        return normalized == null ? "" : normalized;
    }

    private static String toPortugueseTitleCase(String value) {
        var words = value.toLowerCase(Locale.ROOT).split(" ");
        for (var index = 0; index < words.length; index++) {
            if (index > 0 && List.of("da", "das", "de", "do", "dos", "e").contains(words[index])) {
                continue;
            }

            if (!words[index].isBlank()) {
                words[index] = words[index].substring(0, 1).toUpperCase(Locale.ROOT) + words[index].substring(1);
            }
        }

        return String.join(" ", words);
    }
}