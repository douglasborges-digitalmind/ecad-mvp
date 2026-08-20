package br.com.ecad.captacao.shared.infrastructure.sga;

/**
 * Configuração de conexão com o SGA, usada pelo SgaCredentialsProvider.
 */
public record SgaConnectionSettings(
    String oauthUrl,
    String baseUrl,
    String clientId,
    String clientSecret,
    String authorization,
    String user,
    int timeoutSeconds
) {
    public static final int DEFAULT_TIMEOUT_SECONDS = 30;

    public static SgaConnectionSettings of(String oauthUrl, String baseUrl, String clientId,
                                            String clientSecret, String authorization, String user) {
        return new SgaConnectionSettings(
            oauthUrl != null ? oauthUrl : "https://api-prd.ecad.org.br/oauth-rfc/v1/access-token",
            baseUrl != null ? baseUrl : "https://backend.ecad.org.br/arrecadacao/api-show",
            clientId != null ? clientId : "",
            clientSecret != null ? clientSecret : "",
            authorization != null ? authorization : "",
            user != null ? user : "",
            DEFAULT_TIMEOUT_SECONDS
        );
    }

    public boolean hasStaticAuthorization() {
        return authorization != null && !authorization.isBlank();
    }

    public boolean hasClientCredentials() {
        return clientId != null && !clientId.isBlank()
            && clientSecret != null && !clientSecret.isBlank();
    }
}