package br.com.ecad.captacao.controlcenter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import br.com.ecad.captacao.shared.common.LruCache;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(1)
@ConditionalOnProperty(name = "CONTROL_CENTER_RATE_LIMIT_ENABLED", havingValue = "true", matchIfMissing = true)
class RateLimitFilter extends OncePerRequestFilter {
    private static final int PERMIT_LIMIT = 120;
    private static final long WINDOW_MILLIS = 60_000L;
    /**
     * Limite de chaves (IPs) rastreadas simultaneamente. Quando excedido, o cache LRU descarta
     * a chave menos recentemente usada. 50.000 IPs cobrem cenarios de alta cardinalidade sem
     * consumir memoria excessiva (cada entrada ~ 50 bytes).
     */
    private static final int MAX_TRACKED_KEYS = 50_000;

    private final LruCache<String, Window> windows = new LruCache<>(MAX_TRACKED_KEYS);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        if (!acquire(resolvePartitionKey(request))) {
            response.setStatus(429);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean acquire(String key) {
        var now = Instant.now().toEpochMilli();
        var window = windows.get(key).orElseGet(() -> {
            var fresh = new Window();
            windows.put(key, fresh);
            return fresh;
        });
        synchronized (window) {
            if (now - window.startedAtMillis.get() >= WINDOW_MILLIS) {
                window.startedAtMillis.set(now);
                window.count.set(0);
            }

            if (window.count.get() >= PERMIT_LIMIT) {
                return false;
            }

            window.count.incrementAndGet();
            return true;
        }
    }

    private static String resolvePartitionKey(HttpServletRequest request) {
        var remoteAddress = request.getRemoteAddr();
        return remoteAddress == null || remoteAddress.isBlank() ? "unknown" : remoteAddress;
    }

    private static final class Window {
        final AtomicLong startedAtMillis = new AtomicLong(System.currentTimeMillis());
        final AtomicInteger count = new AtomicInteger();
    }
}