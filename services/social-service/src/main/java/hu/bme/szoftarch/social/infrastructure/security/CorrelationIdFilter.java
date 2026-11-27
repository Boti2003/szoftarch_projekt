package hu.bme.szoftarch.social.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Inbound filter that establishes a per-request correlation id for end-to-end
 * traceability (requirement 12.1).
 *
 * <p>It reads the correlation-id header (name configurable via
 * {@code social.correlation-header}, falling back to
 * {@code social.internal.correlation-header}, default {@code X-Correlation-Id}),
 * generating a fresh UUID when the inbound request carries none. The id is
 * placed in the SLF4J {@link MDC} under the key {@code correlationId} — the same
 * key the outbound adapters' {@code CorrelationIdAccessor}s and
 * {@code GlobalExceptionHandler} read — so inbound requests, outbound internal
 * calls, log lines, and problem {@code traceId}s all share one id. The id is
 * echoed back on the response header and the MDC is always cleared in a
 * {@code finally} block so ids never leak across pooled threads.
 *
 * <p>Ordered {@link Ordered#HIGHEST_PRECEDENCE highest precedence} so it runs
 * before the security filter chain, ensuring even a 401 rejection carries the
 * correlation id. No header values beyond the correlation id are read, and no
 * token or key material is logged.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    /** MDC key shared with the adapters and the error handler. */
    static final String MDC_KEY = "correlationId";
    static final String DEFAULT_HEADER = "X-Correlation-Id";

    private final String headerName;

    public CorrelationIdFilter(
            @Value("${social.correlation-header:${social.internal.correlation-header:X-Correlation-Id}}")
            String headerName) {
        this.headerName = (headerName == null || headerName.isBlank()) ? DEFAULT_HEADER : headerName;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String correlationId = resolve(request.getHeader(headerName));
        MDC.put(MDC_KEY, correlationId);
        response.setHeader(headerName, correlationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    private static String resolve(String inbound) {
        if (inbound != null && !inbound.isBlank()) {
            return inbound.trim();
        }
        return UUID.randomUUID().toString();
    }
}
