package br.com.ecad.captacao.controlcenter;

import java.io.IOException;
import java.util.UUID;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(0)
class RequestCorrelationFilter extends OncePerRequestFilter {
    static final String CORRELATION_ID_HEADER_NAME = "X-Correlation-ID";
    static final String TRACE_ID_HEADER_NAME = "X-Trace-ID";
    static final String RESPONSE_TIME_HEADER_NAME = "X-Response-Time-Ms";
    static final String CORRELATION_ID_ATTRIBUTE = "request_correlation_id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        var started = System.nanoTime();
        var correlationId = resolveCorrelationId(request);
        request.setAttribute(CORRELATION_ID_ATTRIBUTE, correlationId);
        response.setHeader(CORRELATION_ID_HEADER_NAME, correlationId);
        response.setHeader(TRACE_ID_HEADER_NAME, request.getRequestId());
        response.setHeader(RESPONSE_TIME_HEADER_NAME, "0");

        try {
            filterChain.doFilter(request, response);
        } finally {
            if (!response.isCommitted()) {
                var elapsedMillis = (System.nanoTime() - started) / 1_000_000L;
                response.setHeader(RESPONSE_TIME_HEADER_NAME, Long.toString(elapsedMillis));
            }
        }
    }

    private static String resolveCorrelationId(HttpServletRequest request) {
        var provided = request.getHeader(CORRELATION_ID_HEADER_NAME);
        if (provided != null && !provided.isBlank()) {
            return provided.trim();
        }

        return request.getRequestId() == null ? UUID.randomUUID().toString() : request.getRequestId();
    }
}