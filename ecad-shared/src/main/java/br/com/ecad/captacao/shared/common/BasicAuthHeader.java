package br.com.ecad.captacao.shared.common;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Constroi cabecalhos HTTP Basic conforme RFC 7617 / OAuth 2.0 RFC 6749 secao 2.3.1.
 * Garante que client_id e client_secret sejam codificados em application/x-www-form-urlencoded
 * antes de serem concatenados e codificados em Base64 - regra obrigatoria que estava omissa em
 * alguns clientes do projeto e poderia gerar 401 quando o secret contem caracteres reservados.
 */
public final class BasicAuthHeader {

    private BasicAuthHeader() {
    }

    /** Retorna o valor completo do header "Basic <base64>". */
    public static String headerValue(String clientId, String clientSecret) {
        return "Basic " + encode(clientId, clientSecret);
    }

    /** Retorna apenas o token Base64, sem o prefixo "Basic ". */
    public static String encode(String clientId, String clientSecret) {
        var encodedId = URLEncoder.encode(safe(clientId), StandardCharsets.UTF_8);
        var encodedSecret = URLEncoder.encode(safe(clientSecret), StandardCharsets.UTF_8);
        var raw = encodedId + ":" + encodedSecret;
        return Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
