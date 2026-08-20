package br.com.ecad.captacao.shared.common;

import java.util.concurrent.Callable;

import org.slf4j.Logger;

/**
 * Politica de retentativa com backoff exponencial limitado. Centraliza o padrao "try N vezes com
 * sleep 2^n * base capado em max" usado por integracoes HTTP do projeto (SGA, AI Provider Chain, etc).
 *
 * Intencional: nao instancia executors nem usa CompletableFuture; mantem comportamento sincrono
 * compativel com os schedulers e consumidores existentes.
 */
public final class RetryPolicy {
    private final int maxAttempts;
    private final long baseBackoffMs;
    private final long maxBackoffMs;

    private RetryPolicy(int maxAttempts, long baseBackoffMs, long maxBackoffMs) {
        this.maxAttempts = Math.max(1, maxAttempts);
        this.baseBackoffMs = Math.max(0L, baseBackoffMs);
        this.maxBackoffMs = Math.max(this.baseBackoffMs, maxBackoffMs);
    }

    public static RetryPolicy of(int maxAttempts, long baseBackoffMs, long maxBackoffMs) {
        return new RetryPolicy(maxAttempts, baseBackoffMs, maxBackoffMs);
    }

    /** Calcula o backoff (em ms) da tentativa zero-based, limitado por maxBackoffMs. */
    public long backoffMillis(int attemptIndex) {
        var exponent = Math.max(0, attemptIndex);
        if (exponent > 30) {
            return maxBackoffMs;
        }
        var raw = baseBackoffMs * (1L << exponent);
        return Math.min(maxBackoffMs, raw);
    }

    public int maxAttempts() {
        return maxAttempts;
    }

    /**
     * Executa o callable com retentativas. Se todas falharem, propaga a ultima excecao.
     * Excecoes InterruptedException sao propagadas imediatamente preservando o flag de interrupcao.
     */
    public <T> T execute(Callable<T> action, Logger logger, String operationLabel) throws Exception {
        Exception last = null;
        for (var attempt = 0; attempt < maxAttempts; attempt++) {
            try {
                return action.call();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw ex;
            } catch (Exception ex) {
                last = ex;
                if (attempt >= maxAttempts - 1) {
                    break;
                }
                if (logger != null) {
                    logger.warn("Falha em {} (tentativa {}/{}): {}", operationLabel, attempt + 1, maxAttempts, ex.getMessage());
                }
                Thread.sleep(backoffMillis(attempt));
            }
        }
        throw last == null ? new IllegalStateException("Retry exausted sem excecao registrada para " + operationLabel) : last;
    }
}
