package br.com.ecad.captacao.shared.common;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Constroi URIs com query string codificada em application/x-www-form-urlencoded preservando a ordem
 * de insercao. Centraliza a politica de URL encoding usada por todos os clientes HTTP do ECAD para
 * evitar implementacoes divergentes.
 */
public final class UriQueryBuilder {
    private final String baseUrl;
    private final String path;
    private final Map<String, String> params = new LinkedHashMap<>();

    private UriQueryBuilder(String baseUrl, String path) {
        this.baseUrl = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
        this.path = path == null ? "" : path;
    }

    public static UriQueryBuilder from(String baseUrl, String path) {
        return new UriQueryBuilder(baseUrl, path);
    }

    public UriQueryBuilder param(String key, String value) {
        params.put(key, value == null ? "" : value);
        return this;
    }

    public UriQueryBuilder paramIfPresent(String key, String value) {
        if (value != null && !value.isBlank()) {
            params.put(key, value);
        }
        return this;
    }

    public UriQueryBuilder paramIfPresent(String key, Object value) {
        if (value != null) {
            params.put(key, value.toString());
        }
        return this;
    }

    public String buildString() {
        if (params.isEmpty()) {
            return baseUrl + path;
        }
        var sb = new StringBuilder(baseUrl).append(path).append('?');
        var first = true;
        for (var entry : params.entrySet()) {
            if (!first) {
                sb.append('&');
            }
            first = false;
            sb.append(encode(entry.getKey())).append('=').append(encode(entry.getValue()));
        }
        return sb.toString();
    }

    public URI build() {
        return URI.create(buildString());
    }

    public static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
