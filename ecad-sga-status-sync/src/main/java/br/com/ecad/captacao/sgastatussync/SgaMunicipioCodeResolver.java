package br.com.ecad.captacao.sgastatussync;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import br.com.ecad.captacao.shared.TextNormalization;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
class SgaMunicipioCodeResolver {
    private static final Logger LOGGER = LoggerFactory.getLogger(SgaMunicipioCodeResolver.class);

    private final Map<Key, Integer> codes;

    SgaMunicipioCodeResolver(SgaStatusSyncSettings settings) {
        this.codes = loadCodes(settings.municipioCsvPath());
    }

    Integer resolve(String municipio, String uf) {
        return codes.get(new Key(TextNormalization.normalizeForComparison(uf), TextNormalization.normalizeForComparison(municipio)));
    }

    static Map<Key, Integer> loadCodes(String csvPath) {
        var result = new HashMap<Key, Integer>();
        if (csvPath == null || csvPath.isBlank()) {
            return result;
        }
        var path = Path.of(csvPath);
        if (!Files.exists(path)) {
            LOGGER.warn("CSV de municipios SGA nao encontrado: {}", csvPath);
            return result;
        }
        try {
            var lines = readLines(path);
            if (lines.isEmpty()) {
                return result;
            }
            var header = header(lines.getFirst());
            for (var index = 1; index < lines.size(); index++) {
                var columns = splitCsvLine(lines.get(index));
                var uf = value(columns, header, "UF");
                var municipio = firstNonBlank(value(columns, header, "MUNICIPIO"), value(columns, header, "MUNIC\u00cdPIO"));
                var codeText = value(columns, header, "COD_MUNICIPIOECAD");
                if (uf != null && municipio != null && codeText != null) {
                    try {
                        result.put(new Key(TextNormalization.normalizeForComparison(uf), TextNormalization.normalizeForComparison(municipio)), Integer.parseInt(codeText));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            LOGGER.info("CSV de municipios SGA carregado: {} registros ({})", result.size(), csvPath);
        } catch (Exception ex) {
            LOGGER.warn("Falha ao carregar CSV de municipios SGA: {}", csvPath, ex);
        }
        return result;
    }

    private static List<String> readLines(Path path) throws Exception {
        var bytes = Files.readAllBytes(path);
        var text = new String(bytes, StandardCharsets.UTF_8);
        if (text.indexOf('\uFFFD') >= 0) {
            text = new String(bytes, java.nio.charset.Charset.forName("windows-1252"));
        }
        return text.lines().toList();
    }

    private static Map<String, Integer> header(String headerLine) {
        var result = new HashMap<String, Integer>();
        var columns = splitCsvLine(headerLine);
        for (var index = 0; index < columns.size(); index++) {
            var name = TextNormalization.normalizeForComparison(columns.get(index));
            if (!name.isBlank()) {
                result.put(name, index);
            }
        }
        return result;
    }

    private static String value(List<String> columns, Map<String, Integer> header, String name) {
        var index = header.get(TextNormalization.normalizeForComparison(name));
        return index != null && index < columns.size() ? columns.get(index) : null;
    }

    static List<String> splitCsvLine(String line) {
        if (line == null || line.isBlank()) {
            return List.of();
        }
        var columns = new java.util.ArrayList<String>();
        var current = new StringBuilder();
        var insideQuotes = false;
        for (var index = 0; index < line.length(); index++) {
            var ch = line.charAt(index);
            if (ch == '"') {
                insideQuotes = !insideQuotes;
                continue;
            }
            if (ch == ',' && !insideQuotes) {
                columns.add(current.toString().trim());
                current.setLength(0);
                continue;
            }
            current.append(ch);
        }
        columns.add(current.toString().trim());
        return columns;
    }

    private static String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }

    record Key(String uf, String municipio) {
    }
}
