package br.com.ecad.captacao.sgastatussync;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import br.com.ecad.captacao.shared.JsonDefaults;
import br.com.ecad.captacao.shared.common.LruCache;
import br.com.ecad.captacao.shared.common.RetryPolicy;
import br.com.ecad.captacao.shared.common.Strings;
import br.com.ecad.captacao.shared.common.UriQueryBuilder;
import br.com.ecad.captacao.shared.domain.enums.StatusSGA;
import br.com.ecad.captacao.shared.infrastructure.sga.SgaAuthException;
import br.com.ecad.captacao.shared.infrastructure.sga.SgaCredentialsProvider;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
class HttpSgaApiClient implements SgaApiClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpSgaApiClient.class);
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);
    private static final long MAX_BACKOFF_MS = 30_000L;
    private static final int MAX_CACHE_SIZE = 10_000;

    private final HttpClient httpClient;
    private final SgaStatusSyncSettings settings;
    private final SgaEventMatcher matcher;
    private final SgaCredentialsProvider credentialsProvider;
    private final RetryPolicy retryPolicy;
    // Cache LRU verdadeiro (access-order LinkedHashMap) com TTL.
    // Substitui ConcurrentHashMap+iterator.next().remove() que removia entrada arbitraria.
    private final LruCache<CacheKey, CachedCandidates> cache = new LruCache<>(MAX_CACHE_SIZE, CACHE_TTL);
    HttpSgaApiClient(HttpClient sgaHttpClient, SgaStatusSyncSettings settings, SgaEventMatcher matcher,
                     SgaCredentialsProvider credentialsProvider) {
        this.httpClient = sgaHttpClient;
        this.settings = settings;
        this.matcher = matcher;
        this.credentialsProvider = credentialsProvider;
        this.retryPolicy = RetryPolicy.of(settings.sgaMaxRetries(), 1000, MAX_BACKOFF_MS);
    }

    @Override
    public SgaVerificationResult verificarEvento(SgaEventQuery query) throws Exception {
        if (isBlank(query.tituloEvento()) || isBlank(query.municipio()) || isBlank(query.uf())) {
            return new SgaVerificationResult(StatusSGA.NAO_VERIFICADO, new SgaMatchResult(), 0, false);
        }
        try {
            return retryPolicy.execute(() -> {
                try {
                    var fetched = fetchShows(query);
                    var match = matcher.findBestValidMatch(query, fetched.candidates());
                    var status = match.found ? StatusSGA.JA_CADASTRADO : StatusSGA.INEDITO;
                    LOGGER.info("SGA v3: {} | candidatos={} cache={} tituloScore={} municipioScore={} pos={} evento={}",
                        status, fetched.candidates().size(), fetched.fromCache(),
                        match.titleScore, match.municipioScore, match.candidatePosition,
                        query.tituloEvento());
                    return new SgaVerificationResult(status, match, fetched.candidates().size(), fetched.fromCache());
                } catch (SgaAuthException ex) {
                    credentialsProvider.clearCache();
                    throw ex;
                }
            }, LOGGER, "SGA verificação v3");
        } catch (Exception ex) {
            LOGGER.error("SGA v3 indisponivel apos {} tentativas - retornando NAO_VERIFICADO",
                settings.sgaMaxRetries(), ex);
            return new SgaVerificationResult(StatusSGA.NAO_VERIFICADO, new SgaMatchResult(), 0, false);
        }
    }

    private FetchShowsResult fetchShows(SgaEventQuery query) throws Exception {
        var cacheKey = new CacheKey(query.dataRealizacao(),
            query.uf().trim().toUpperCase(java.util.Locale.ROOT), query.codMunicipio());
        // LruCache com TTL: get() retorna Optional.empty() se expirado e remove proativamente.
        var cached = cache.get(cacheKey);
        if (cached.isPresent()) {
            return new FetchShowsResult(cached.get().candidates(), true);
        }

        // Eviction LRU verdadeiro: a propria LruCache.remove() entradas excedentes em put().
        var token = credentialsProvider.getToken();
        var requestBuilder = HttpRequest.newBuilder(URI.create(buildShowsUrl(query, cacheKey)))
            .timeout(Duration.ofSeconds(settings.sgaTimeoutSeconds()))
            .header("Authorization", "Bearer " + token)
            .GET();
        if (!settings.sgaUser().isBlank()) {
            requestBuilder.header("USER", settings.sgaUser());
        }
        var response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 401) {
            throw new SgaAuthException("SGA retornou 401 - autenticação expirada");
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("SGA retornou HTTP " + response.statusCode());
        }
        var candidates = parseCandidates(response.body());
        // TTL e gerenciado internamente pela LruCache; usamos Instant.MAX para sinalizar "sempre valido
        // ate o TTL interno" e mantemos compatibilidade com o record CachedCandidates.
        cache.put(cacheKey, new CachedCandidates(candidates, Instant.MAX));
        return new FetchShowsResult(candidates, false);
    }

    private String buildShowsUrl(SgaEventQuery query, CacheKey cacheKey) {
        var dateParam = query.dataRealizacao().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) + " 00:00:00";
        return UriQueryBuilder.from(settings.sgaBaseUrl(), "/shows")
            .param("dataInicio", dateParam)
            .param("dataFim", dateParam)
            .param("uf", cacheKey.uf())
            .param("start", "0")
            .param("status", "TODOS")
            .param("limit", Integer.toString(settings.sgaResultLimit()))
            .paramIfPresent("codMunicipio", cacheKey.codMunicipio())
            .buildString();
    }

    static List<SgaShowCandidate> parseCandidates(String json) throws Exception {
        var root = JsonDefaults.objectMapper().readTree(json);
        var body = root.path("body");
        if (!body.isArray()) {
            return List.of();
        }
        var candidates = new java.util.ArrayList<SgaShowCandidate>();
        for (JsonNode item : body) {
            candidates.add(new SgaShowCandidate(
                text(item, "titulo"), parseApiDate(text(item, "dataPrevista")),
                text(item, "municipio"), text(item, "codigo"), text(item, "status")));
        }
        return candidates;
    }

    static LocalDate parseApiDate(String value) {
        if (value == null || value.isBlank()) {
        return null;
    }
        try {
            return OffsetDateTime.parse(value).toLocalDate();
        } catch (Exception ignored) {
    }
        var datePart = value.split(" ")[0];
        for (var formatter : List.of(
            DateTimeFormatter.ISO_LOCAL_DATE, DateTimeFormatter.ofPattern("dd/MM/yyyy"))) {
            try {
                return LocalDate.parse(datePart, formatter);
            } catch (Exception ignored) {
    }
    }
        return null;
    }

    private static String text(JsonNode item, String field) {
        var node = item.path(field);
        return node.isMissingNode() || node.isNull() ? "" : node.asText("");
    }

    private static boolean isBlank(String value) {
        return Strings.isBlank(value);
    }

    private record CacheKey(LocalDate data, String uf, Integer codMunicipio) {
}

    private record CachedCandidates(List<SgaShowCandidate> candidates, Instant expiresAt) {
    }

    private record FetchShowsResult(List<SgaShowCandidate> candidates, boolean fromCache) {
    }
}

