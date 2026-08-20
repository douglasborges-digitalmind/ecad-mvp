package br.com.ecad.captacao.controlcenter.services;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

import br.com.ecad.captacao.controlcenter.models.SetupPncpUrlsItemResult;
import br.com.ecad.captacao.controlcenter.models.SetupPncpUrlsRequest;
import br.com.ecad.captacao.controlcenter.models.SetupPncpUrlsResult;
import br.com.ecad.captacao.shared.TextNormalization;
import org.springframework.stereotype.Service;

@Service
public class PncpUrlSetupService {
    private static final List<String> OUTPUT_HEADERS = List.of("cnpj", "municipio", "uf", "unidade_ecad", "id_pncp", "url_busca", "status", "erro");
    private static final Charset WINDOWS_1252 = Charset.forName("windows-1252");

    private final PncpMunicipiosCatalog municipiosCatalog;

    public PncpUrlSetupService(PncpMunicipiosCatalog municipiosCatalog) {
        this.municipiosCatalog = municipiosCatalog;
    }

    public SetupPncpUrlsResult setup(SetupPncpUrlsRequest request) throws Exception {
        if (request == null || (isBlank(request.csvPath()) && isBlank(request.csvBase64()))) {
            throw new IllegalArgumentException("csv_path ou csv_base64 e obrigatorio.");
        }

        Path inputPath = null;
        String inputSource;
        byte[] csvBytes;
        if (!isBlank(request.csvBase64())) {
            csvBytes = decodeCsvBase64(request.csvBase64());
            inputSource = "payload:base64";
        } else {
            inputPath = Path.of(request.csvPath()).toAbsolutePath().normalize();
            if (!Files.exists(inputPath)) {
                throw new FileNotFoundException("Arquivo CSV nao encontrado: " + inputPath);
            }
            csvBytes = Files.readAllBytes(inputPath);
            inputSource = inputPath.toString();
        }

        var outputPath = resolveOutputPath(inputPath, request.outputPath());
        var rateLimitSeconds = request.rateLimitSeconds() == null ? 1.0d : request.rateLimitSeconds();
        if (rateLimitSeconds < 0d) {
            throw new IllegalArgumentException("rate_limit_seconds deve ser maior ou igual a zero.");
        }

        var warnings = new ArrayList<String>();
        var entries = readEntries(csvBytes, warnings);
        if (entries.isEmpty()) {
            throw new IllegalArgumentException("Nenhum CNPJ valido encontrado.");
        }

        var results = new ArrayList<SetupPncpUrlsItemResult>();

        for (var index = 0; index < entries.size(); index++) {
            var entry = entries.get(index);
            try {
                var catalogItem = municipiosCatalog.find(entry.municipio(), entry.uf())
                    .orElseThrow(() -> new IllegalArgumentException("Municipio/UF nao encontrado em municipiosPNCP.json: " + entry.municipio() + "/" + entry.uf()));
                var unidadeEcad = resolveUnidadeEcad(entry.unidadeEcad(), entry.uf(), catalogItem.unidadeEcad());
                results.add(new SetupPncpUrlsItemResult(entry.cnpj(), catalogItem.municipio(), catalogItem.uf(), unidadeEcad, catalogItem.idPncp(), catalogItem.url(), "sucesso", ""));
            } catch (Exception ex) {
                results.add(new SetupPncpUrlsItemResult(entry.cnpj(), entry.municipio(), entry.uf(), resolveUnidadeEcad(entry.unidadeEcad(), entry.uf(), ""), "", "", "erro", ex.getMessage()));
            }

            if (index < entries.size() - 1 && rateLimitSeconds > 0d) {
                Thread.sleep((long) (rateLimitSeconds * 1000));
            }
        }

        var generatedCsv = buildResultsCsv(results);
        writeResults(outputPath, generatedCsv);
        var sucessos = (int) results.stream().filter(item -> "sucesso".equals(item.status())).count();
        var erros = results.size() - sucessos;
        return new SetupPncpUrlsResult(
            inputSource,
            outputPath == null ? null : outputPath.toString(),
            generatedCsv,
            entries.size(),
            sucessos,
            erros,
            warnings,
            List.copyOf(results));
    }

    private static Path resolveOutputPath(Path inputPath, String outputPath) {
        if (isBlank(outputPath)) {
            if (inputPath == null) {
                return null;
            }
            var parent = inputPath.getParent();
            return (parent == null ? Path.of("resultado_pncp.csv") : parent.resolve("resultado_pncp.csv")).toAbsolutePath().normalize();
        }

        return Path.of(outputPath).toAbsolutePath().normalize();
    }

    private static List<CnpjEntry> readEntries(byte[] csvBytes, List<String> warnings) throws IOException {
        var lines = readAllLinesWithEncodingFallback(csvBytes);
        var headerIndex = findFirstNonEmptyLine(lines);
        if (headerIndex < 0) {
            throw new IllegalArgumentException("CSV nao possui cabecalho.");
        }

        var delimiter = inferDelimiter(lines.get(headerIndex));
        var headers = parseCsvLine(lines.get(headerIndex), delimiter).stream().map(PncpUrlSetupService::normalizeHeader).toList();
        var cnpjIndex = headers.indexOf("CNPJ");
        var municipioIndex = headers.indexOf("MUNICIPIO");
        var ufIndex = headers.indexOf("UF");
        var unidadeEcadIndex = headers.indexOf("UNIDADEECAD");
        if (cnpjIndex < 0) {
            throw new IllegalArgumentException("Coluna obrigatoria ausente no CSV: cnpj");
        }

        var entries = new ArrayList<CnpjEntry>();
        for (var index = headerIndex + 1; index < lines.size(); index++) {
            var line = lines.get(index);
            if (line.isBlank()) {
                continue;
            }

            var fields = parseCsvLine(line, delimiter);
            try {
                entries.add(CnpjEntry.create(
                    getField(fields, cnpjIndex),
                    municipioIndex >= 0 ? getField(fields, municipioIndex) : "",
                    ufIndex >= 0 ? getField(fields, ufIndex) : "",
                    unidadeEcadIndex >= 0 ? getField(fields, unidadeEcadIndex) : ""));
            } catch (IllegalArgumentException ex) {
                warnings.add("Linha " + (index + 1) + " ignorada: " + ex.getMessage());
            }
        }

        return entries;
    }

    private static List<String> readAllLinesWithEncodingFallback(byte[] csvBytes) throws IOException {
        try {
            return readAllLines(csvBytes, StandardCharsets.UTF_8);
        } catch (CharacterCodingException ex) {
            return readAllLines(csvBytes, WINDOWS_1252);
        }
    }

    private static List<String> readAllLines(byte[] csvBytes, Charset charset) throws IOException {
        var decoder = charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT);

        try (var reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(csvBytes), decoder))) {
            var lines = new ArrayList<String>();
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }

            return lines;
        }
    }

    private static String buildResultsCsv(List<SetupPncpUrlsItemResult> results) {
        var lines = new ArrayList<String>();
        lines.add(String.join(";", OUTPUT_HEADERS));
        for (var result : results) {
            lines.add(String.join(";", List.of(
                escapeCsv(result.cnpj()),
                escapeCsv(result.municipio()),
                escapeCsv(result.uf()),
                escapeCsv(result.unidadeEcad()),
                escapeCsv(result.idPncp()),
                escapeCsv(result.urlBusca()),
                escapeCsv(result.status()),
                escapeCsv(result.erro()))));
        }

        return String.join(System.lineSeparator(), lines);
    }

    private static void writeResults(Path outputPath, String csvContent) throws IOException {
        if (outputPath == null) {
            return;
        }

        var parent = outputPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(outputPath, csvContent, StandardCharsets.UTF_8);
    }

    private static byte[] decodeCsvBase64(String csvBase64) {
        try {
            return Base64.getDecoder().decode(csvBase64);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("csv_base64 invalido.", ex);
        }
    }

    private static String resolveUnidadeEcad(String unidadeEcad, String uf, String fallback) {
        if (!isBlank(unidadeEcad)) {
            return unidadeEcad.trim().toUpperCase(Locale.ROOT);
        }

        if (!isBlank(fallback)) {
            return fallback.trim().toUpperCase(Locale.ROOT);
        }

        return switch (normalizeUf(uf)) {
            case "AC" -> "ACRE";
            case "AL" -> "ALAGOAS";
            case "AP" -> "AMAPA";
            case "AM" -> "AMAZONAS";
            case "BA" -> "BAHIA";
            case "CE" -> "CEARA";
            case "DF" -> "DISTRITO FEDERAL";
            case "ES" -> "ESPIRITO SANTO";
            case "GO" -> "GOIAS";
            case "MA" -> "MARANHAO";
            case "MT" -> "MATO GROSSO";
            case "MS" -> "MATO GROSSO DO SUL";
            case "MG" -> "MINAS GERAIS";
            case "PA" -> "PARA";
            case "PB" -> "PARAIBA";
            case "PR" -> "PARANA";
            case "PE" -> "PERNAMBUCO";
            case "PI" -> "PIAUI";
            case "RJ" -> "RIO DE JANEIRO";
            case "RN" -> "RIO GRANDE DO NORTE";
            case "RS" -> "RIO GRANDE DO SUL";
            case "RO" -> "RONDONIA";
            case "RR" -> "RORAIMA";
            case "SC" -> "SANTA CATARINA";
            case "SP" -> "SAO PAULO";
            case "SE" -> "SERGIPE";
            case "TO" -> "TOCANTINS";
            default -> "";
        };
    }

    private static String normalizeUf(String uf) {
        return isBlank(uf) ? "" : uf.trim().toUpperCase(Locale.ROOT);
    }

    private static MunicipioUf splitMunicipioUf(String municipio) {
        if (isBlank(municipio)) {
            return new MunicipioUf("", "");
        }

        var normalized = municipio.trim();
        var separatorIndex = normalized.lastIndexOf('/');
        if (separatorIndex <= 0 || separatorIndex >= normalized.length() - 1) {
            return new MunicipioUf(normalized, "");
        }

        var possibleUf = normalized.substring(separatorIndex + 1).trim();
        if (possibleUf.length() != 2 || !possibleUf.chars().allMatch(Character::isLetter)) {
            return new MunicipioUf(normalized, "");
        }

        return new MunicipioUf(normalized.substring(0, separatorIndex).trim(), possibleUf.toUpperCase(Locale.ROOT));
    }

    private static String normalizeHeader(String value) {
        var normalized = TextNormalization.normalizeForComparison(value);
        return normalized.chars()
            .filter(Character::isLetterOrDigit)
            .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
            .toString();
    }

    private static int findFirstNonEmptyLine(List<String> lines) {
        for (var index = 0; index < lines.size(); index++) {
            if (!lines.get(index).isBlank()) {
                return index;
            }
        }

        return -1;
    }

    private static char inferDelimiter(String headerLine) {
        return headerLine.indexOf(';') >= 0 ? ';' : ',';
    }

    private static List<String> parseCsvLine(String line, char delimiter) {
        var values = new ArrayList<String>();
        var builder = new StringBuilder();
        var inQuotes = false;
        for (var index = 0; index < line.length(); index++) {
            var ch = line.charAt(index);
            if (ch == '"') {
                if (inQuotes && index + 1 < line.length() && line.charAt(index + 1) == '"') {
                    builder.append('"');
                    index++;
                } else {
                    inQuotes = !inQuotes;
                }
                continue;
            }

            if (ch == delimiter && !inQuotes) {
                values.add(builder.toString().trim());
                builder.setLength(0);
                continue;
            }

            builder.append(ch);
        }

        values.add(builder.toString().trim());
        return values;
    }

    private static String getField(List<String> fields, int index) {
        return index >= 0 && index < fields.size() ? fields.get(index).trim() : "";
    }

    private static String escapeCsv(String value) {
        var safe = value == null ? "" : value;
        if (safe.indexOf(';') >= 0 || safe.indexOf('"') >= 0 || safe.indexOf('\n') >= 0 || safe.indexOf('\r') >= 0) {
            return '"' + safe.replace("\"", "\"\"") + '"';
        }
        return safe;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record CnpjEntry(String cnpj, String municipio, String uf, String unidadeEcad) {
        private static CnpjEntry create(String cnpj, String municipio, String uf, String unidadeEcad) {
            var normalized = digits(cnpj);
            if (normalized.length() != 14) {
                throw new IllegalArgumentException("CNPJ deve ter 14 digitos: " + cnpj);
            }

            var municipioNormalizado = municipio == null ? "" : municipio.trim();
            var ufNormalizada = normalizeUf(uf);
            if (isBlank(ufNormalizada)) {
                var municipioUf = splitMunicipioUf(municipioNormalizado);
                municipioNormalizado = municipioUf.municipio();
                ufNormalizada = municipioUf.uf();
            }

            return new CnpjEntry(normalized, municipioNormalizado, ufNormalizada, resolveUnidadeEcad(unidadeEcad, ufNormalizada, ""));
        }

        private static String digits(String value) {
            if (value == null) {
                return "";
            }
            return value.chars()
                .filter(Character::isDigit)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
        }
    }

    private record MunicipioUf(String municipio, String uf) {
    }
}