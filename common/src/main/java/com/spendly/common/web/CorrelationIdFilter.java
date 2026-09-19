package com.spendly.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Gives every request a correlation id, so one user action can be traced across services.
 *
 * <p>
 * Reuses an inbound {@code X-Correlation-Id} when the caller supplies one (that is how the id survives a hop between
 * services) and mints one otherwise. The id is exposed three ways: the SLF4J {@link MDC} for log patterns, a request
 * attribute for {@link GlobalExceptionHandler}, and the response header for the client.
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";
    public static final String REQUEST_ATTRIBUTE = CorrelationIdFilter.class.getName() + ".id";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String correlationId = sanitize(request.getHeader(HEADER));
        MDC.put(MDC_KEY, correlationId);
        request.setAttribute(REQUEST_ATTRIBUTE, correlationId);
        response.setHeader(HEADER, correlationId);
        try {
            chain.doFilter(request, response);
        } finally {
            // Servlet threads are pooled; a stale id would leak into the next request's logs.
            MDC.remove(MDC_KEY);
        }
    }

    /**
     * Inbound ids are attacker-controlled and land in log files, so anything that is not a short, boring token is
     * replaced rather than trusted.
     */
    private static String sanitize(@Nullable String inbound) {
        if (inbound == null || inbound.isBlank() || inbound.length() > 64) {
            return UUID.randomUUID().toString();
        }
        String trimmed = inbound.strip();
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            boolean allowed = Character.isLetterOrDigit(c) || c == '-' || c == '_' || c == '.';
            if (!allowed) {
                return UUID.randomUUID().toString();
            }
        }
        return trimmed;
    }

    /** Reads the id for the request in flight, for code that is not in a logging context. */
    public static @Nullable String current() {
        return MDC.get(MDC_KEY);
    }
}
