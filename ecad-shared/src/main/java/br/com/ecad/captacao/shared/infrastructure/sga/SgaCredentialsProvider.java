package br.com.ecad.captacao.shared.infrastructure.sga;

import br.com.ecad.captacao.shared.common.BasicAuthHeader;
import br.com.ecad.captacao.shared.common.RetryPolicy;
import br.com.ecad.captacao.shared.JsonDefaults;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Provedor de token de acesso OAuth client_credentials para o SGA (ECAD).
 * Cacheia o token com TTL configurável (default 1h) e é thread-safe.
 * Se SGA_AUTHORIZATION estiver definido (token estático), retorna-o diretamente.
 * 
 * Uso:
 *   SgaCredentialsProvider provider = new SgaCredentialsProvider(settings);
 *   String token = provider.getToken();
 */
public class SgaCredentialsProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(SgaCredentialsProvider.class);
    private static final long DEFAULT_TOKEN_TTL_SECONDS = 3600; // 1 hora

    private final SgaConnectionSettings settings;
    private final Supplier<HttpClient> httpClientFactory;
    private final ReentrantLock lock = new ReentrantLock();
    private volatile String cachedToken;
    private volatile Instant tokenExpiry = Instant.EPOCH;

    public SgaCredentialsProvider(SgaConnectionSettings settings) {
        this(settings, HttpClient::newHttpClient);
    }

    // Package-private para teste
    SgaCredentialsProvider(SgaConnectionSettings settings, Supplier<HttpClient> httpClientFactory) {
        this.settings = settings;
        this.httpClientFactory = httpClientFactory;
    }

    /**
     * Obtém o token de acesso (Bearer), seja do cache, do SGA_AUTHORIZATION estático
     * ou via OAuth client_credentials.
     * @return token de acesso (sem prefixo "Bearer ")
     * @throws SgaAuthException se a autenticação falhar
     */
    public String getToken() {
        // Se há token estático configurado, retorna diretamente
        if (settings.hasStaticAuthorization()) {
            var raw = settings.authorization().trim();
            return raw.regionMatches(true, 0, "Bearer ", 0, 7) ? raw.substring(7).trim() : raw;
        }

        // Verifica cache
        var token = cachedToken;
        if (token != null && Instant.now().isBefore(tokenExpiry)) {
            return token;
        }

        // Lock para evitar múltiplas chamadas OAuth concorrentes
        lock.lock();
        try {
            // Double-checked locking
            token = cachedToken;
            if (token != null && Instant.now().isBefore(tokenExpiry)) {
                return token;
            }
            token = fetchToken();
            cachedToken = token;
            tokenExpiry = Instant.now().plusSeconds(DEFAULT_TOKEN_TTL_SECONDS);
            return token;
        } finally {
            lock.unlock();
        }
    }

    /** Limpa o cache de token, forçando nova autenticação. */
    public void clearCache() {
        lock.lock();
        try {
            cachedToken = null;
            tokenExpiry = Instant.EPOCH;
        } finally {
            lock.unlock();
        }
    }

    private String fetchToken() {
        if (!settings.hasClientCredentials()) {
            throw new SgaAuthException(
                "Configure SGA_AUTHORIZATION ou SGA_CLIENT_ID/SGA_CLIENT_SECRET para autenticar no SGA.");
        }

        try {
            var encodedCredentials = BasicAuthHeader.encode(settings.clientId(), settings.clientSecret());
            var request = HttpRequest.newBuilder()
                .uri(URI.create(settings.oauthUrl()))
                .timeout(Duration.ofSeconds(Math.max(1, settings.timeoutSeconds())))
                .header("Authorization", "Basic " + encodedCredentials)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("grant_type=client_credentials"))
                .build();

            var client = httpClientFactory.get();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new SgaAuthException("OAuth SGA retornou HTTP " + response.statusCode());
            }

            var token = JsonDefaults.objectMapper()
                .readTree(response.body())
                .path("access_token")
                .asText("");

            if (token.isBlank()) {
                throw new SgaAuthException("Resposta OAuth do SGA sem access_token.");
            }

            LOGGER.info("Token SGA obtido com sucesso. Validade: {}s", DEFAULT_TOKEN_TTL_SECONDS);
            return token;
        } catch (SgaAuthException ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new SgaAuthException("Autenticação SGA interrompida.", ex);
        } catch (Exception ex) {
            throw new SgaAuthException("Falha ao obter token SGA: " + ex.getMessage(), ex);
        }
    }
}