package br.com.ecad.captacao.processingengine;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import br.com.ecad.captacao.shared.TextNormalization;
import br.com.ecad.captacao.shared.domain.entities.Evento;

final class ExtractionFieldNormalizer {
    private static final Set<String> LOWERCASE_TOKENS = Set.of("da", "das", "de", "do", "dos", "e");
    private static final Set<String> UPPERCASE_TOKENS = Set.of("dj", "mc");
    private static final Pattern NON_DIGITS = Pattern.compile("\\D+");

    private ExtractionFieldNormalizer() {
    }

    static void normalize(ExtractionResult result) {
        result.titulo = normalizeFreeText(result.titulo);
        result.local = normalizeFreeText(result.local);
        result.municipio = normalizePlaceName(result.municipio);
        result.uf = normalizeUf(result.uf);
        result.promotorNome = normalizePromoterName(result.promotorNome, result.municipio);
        result.promotorCnpj = normalizeDigits(result.promotorCnpj, 14);
        result.promotorContato = normalizeFreeText(result.promotorContato);
        result.observacoesIa = normalizeFreeText(result.observacoesIa);
        result.interpretes = normalizeNameList(result.interpretes);
    }

    static Evento normalize(Evento evento) {
        return new Evento(
            evento.id(), evento.codigoEvento(),
            normalizeFreeText(evento.titulo()),
            evento.dataInicio(), evento.dataTermino(),
            normalizeFreeText(evento.local()),
            normalizePlaceName(evento.municipio()),
            normalizeUf(evento.uf()),
            evento.unidadeEcad(), evento.hora(),
            normalizeDigits(evento.promotorCnpj(), 14),
            normalizePromoterName(evento.promotorNome(), evento.municipio()),
            normalizeFreeText(evento.promotorContato()),
            normalizeNameList(evento.interpretes()),
            evento.tipoMusica(), evento.cobrancaIngresso(), evento.valorIngresso(), evento.capacidadePublico(),
            evento.status(), evento.statusSga(), evento.nivelCompletude(), evento.fontePrimaria(),
            evento.dataDescoberta(), evento.dataAtualizacao(),
            normalizeFreeText(evento.observacoesIa()),
            evento.idFonteCaptacao(), evento.evidencias()
        );
    }

    static void applyOfficialMunicipality(ExtractionResult result, String municipio, String uf) {
        result.municipio = normalizePlaceName(municipio);
        result.uf = normalizeUf(uf == null ? result.uf : uf);
        result.promotorNome = normalizePromoterName(result.promotorNome, result.municipio);
    }

    static Evento applyOfficialMunicipality(Evento evento, String municipio, String uf) {
        return new Evento(
            evento.id(), evento.codigoEvento(), evento.titulo(), evento.dataInicio(), evento.dataTermino(),
            evento.local(), normalizePlaceName(municipio), normalizeUf(uf == null ? evento.uf() : uf),
            evento.unidadeEcad(), evento.hora(), evento.promotorCnpj(),
            normalizePromoterName(evento.promotorNome(), evento.municipio()), evento.promotorContato(),
            evento.interpretes(), evento.tipoMusica(), evento.cobrancaIngresso(), evento.valorIngresso(),
            evento.capacidadePublico(), evento.status(), evento.statusSga(), evento.nivelCompletude(),
            evento.fontePrimaria(), evento.dataDescoberta(), evento.dataAtualizacao(), evento.observacoesIa(),
            evento.idFonteCaptacao(), evento.evidencias()
        );
    }

    private static String normalizeFreeText(String value) {
        return TextNormalization.normalizeWhitespace(value);
    }

    private static String normalizePlaceName(String value) {
        var normalized = normalizeFreeText(value);
        return normalized == null || normalized.isBlank() ? normalized : titleCase(normalized);
    }

    private static String normalizeUf(String value) {
        var normalized = normalizeFreeText(value);
        if (normalized == null || normalized.isBlank()) {
            return normalized;
        }
        return normalized.length() <= 2 ? normalized.toUpperCase(Locale.ROOT) : titleCase(normalized);
    }

    private static String normalizePromoterName(String value, String canonicalMunicipio) {
        var normalized = normalizeFreeText(value);
        if (normalized == null || normalized.isBlank()) {
            return normalized;
        }
        if (!looksLikePublicEntity(normalized)) {
            return normalized;
        }
        var titleCased = titleCase(normalized);
        if (canonicalMunicipio == null || canonicalMunicipio.isBlank()) {
            return titleCased;
        }
        var promoter = TextNormalization.normalizeForComparison(titleCased);
        var municipio = TextNormalization.normalizeForComparison(canonicalMunicipio);
        if (!promoter.contains(municipio)) {
            return titleCased;
        }
        if (promoter.contains("PREFEITURA MUNICIPAL")) {
            return "Prefeitura Municipal de " + canonicalMunicipio;
        }
        if (promoter.contains("PREFEITURA")) {
            return "Prefeitura de " + canonicalMunicipio;
        }
        if (promoter.contains("MUNICIPIO")) {
            return "Municipio de " + canonicalMunicipio;
        }
        return titleCased;
    }

    private static String normalizeDigits(String value, int expectedLength) {
        var normalized = normalizeFreeText(value);
        if (normalized == null || normalized.isBlank()) {
            return normalized;
        }
        var digits = NON_DIGITS.matcher(normalized).replaceAll("");
        return digits.length() == expectedLength ? digits : normalized;
    }

    private static List<String> normalizeNameList(List<String> values) {
        if (values == null) {
            return new ArrayList<>();
        }
        var normalized = new ArrayList<String>();
        var comparisonKeys = new HashSet<String>();
        for (var value : values) {
            var item = normalizePlaceName(value);
            if (item == null || item.isBlank()) {
                continue;
            }
            var comparisonKey = TextNormalization.normalizeForComparison(item);
            if (!comparisonKeys.add(comparisonKey)) {
                continue;
            }
            normalized.add(item);
        }
        return normalized;
    }

    private static String titleCase(String value) {
        var words = value.toLowerCase(Locale.ROOT).split("\\s+");
        for (var index = 0; index < words.length; index++) {
            var word = words[index];
            if (index > 0 && LOWERCASE_TOKENS.contains(word)) {
                continue;
            }
            if (UPPERCASE_TOKENS.contains(stripAccents(word))) {
                words[index] = word.toUpperCase(Locale.ROOT);
            } else if (!word.isBlank()) {
                words[index] = word.substring(0, 1).toUpperCase(Locale.ROOT) + word.substring(1);
            }
        }
        return String.join(" ", words);
    }

    private static boolean looksLikePublicEntity(String value) {
        var normalized = TextNormalization.normalizeForComparison(value);
        return normalized.contains("PREFEITURA") || normalized.contains("MUNICIPIO") || normalized.contains("SECRETARIA");
    }

    private static String stripAccents(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
    }
}
