package br.com.ecad.captacao.shared.referencedata;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import br.com.ecad.captacao.shared.JsonDefaults;
import br.com.ecad.captacao.shared.TextNormalization;
import com.fasterxml.jackson.core.type.TypeReference;

public final class PncpMunicipiosReferenceCatalog {
    private static final String RESOURCE_PATH = "br/com/ecad/captacao/shared/referencedata/municipiosPNCP.json";
    private static final Pattern MUNICIPIO_ID_PATTERN = Pattern.compile("(?:^|[?&])municipios=([^&]+)");
    private static final AtomicReference<List<PncpMunicipio>> ITEMS_CACHE = new AtomicReference<>();
    private static final AtomicReference<Map<String, PncpMunicipio>> LOOKUP_CACHE = new AtomicReference<>();

    private PncpMunicipiosReferenceCatalog() {
    }

    public static Optional<PncpMunicipio> tryResolve(String municipio, String uf) {
        ensureLoaded();
        return Optional.ofNullable(LOOKUP_CACHE.get().get(normalizeKey(municipio, uf)));
    }

    public static List<PncpMunicipio> getAll() {
        ensureLoaded();
        return ITEMS_CACHE.get();
    }

    private static void ensureLoaded() {
        if (ITEMS_CACHE.get() != null && LOOKUP_CACHE.get() != null) {
            return;
        }

        synchronized (PncpMunicipiosReferenceCatalog.class) {
            if (ITEMS_CACHE.get() != null && LOOKUP_CACHE.get() != null) {
                return;
            }

            var loadedItems = new ArrayList<PncpMunicipio>();
            var loadedLookup = new LinkedHashMap<String, PncpMunicipio>();
            try {
                for (var entry : readRawCatalog().entrySet()) {
                    var item = parseItem(entry.getKey(), entry.getValue());
                    if (item == null) {
                        continue;
                    }

                    loadedItems.add(item);
                    loadedLookup.put(normalizeKey(item.municipio(), item.uf()), item);
                }
            } catch (IOException ex) {
                throw new IllegalStateException("Falha ao carregar municipiosPNCP.json.", ex);
            }

            ITEMS_CACHE.set(List.copyOf(loadedItems));
            LOOKUP_CACHE.set(Map.copyOf(loadedLookup));
        }
    }

    private static Map<String, String> readRawCatalog() throws IOException {
        try (InputStream stream = Thread.currentThread().getContextClassLoader().getResourceAsStream(RESOURCE_PATH)) {
            if (stream == null) {
                throw new IllegalStateException("Arquivo municipiosPNCP.json nao encontrado em " + RESOURCE_PATH + ".");
            }

            return JsonDefaults.objectMapper().readValue(stream, new TypeReference<LinkedHashMap<String, String>>() {
            });
        }
    }

    private static PncpMunicipio parseItem(String municipioUf, String url) {
        if (isBlank(municipioUf) || isBlank(url) || "-".equals(url.trim())) {
            return null;
        }

        var parts = municipioUf.split("/", -1);
        if (parts.length != 2 || isBlank(parts[0]) || isBlank(parts[1])) {
            throw new IllegalArgumentException("Entrada invalida em municipiosPNCP.json: " + municipioUf);
        }

        var municipio = parts[0].trim();
        var uf = parts[1].trim().toUpperCase(Locale.ROOT);
        var unidadeEcad = MunicipioUnidadeReferenceCatalog.tryResolve(municipio, uf)
            .map(item -> item.unidadeEcad)
            .orElseGet(() -> nomeEstado(uf));
        return new PncpMunicipio(municipio, uf, unidadeEcad == null ? "" : unidadeEcad.toUpperCase(Locale.ROOT), extractMunicipioId(url), url.trim());
    }

    private static String extractMunicipioId(String url) {
        var matcher = MUNICIPIO_ID_PATTERN.matcher(url == null ? "" : url);
        return matcher.find() ? matcher.group(1) : "";
    }

    private static String normalizeKey(String municipio, String uf) {
        return TextNormalization.normalizeForComparison(municipio) + "/" + (uf == null ? "" : uf.trim().toUpperCase(Locale.ROOT));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String nomeEstado(String uf) {
        return switch (uf.toUpperCase(Locale.ROOT)) {
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

    public record PncpMunicipio(String municipio, String uf, String unidadeEcad, String idPncp, String url) {
    }
}