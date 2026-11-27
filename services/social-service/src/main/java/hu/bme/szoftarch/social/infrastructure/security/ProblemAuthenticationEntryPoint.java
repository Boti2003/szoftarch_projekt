package hu.bme.szoftarch.social.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

/**
 * Authentication entry point that renders a failed/missing token rejection as a
 * 401 RFC 9457 {@code application/problem+json} body with the stable code
 * {@code UNAUTHENTICATED} (design error table; requirements 4.2, 4.3).
 *
 * <p>The resource server rejects a missing, malformed, invalid-signature, or
 * expired access token <em>before</em> any controller runs, so this case never
 * reaches {@code GlobalExceptionHandler}. This entry point fills that gap and
 * matches the handler's problem conventions exactly: {@code type} is the URN
 * {@code urn:social:error:UNAUTHENTICATED}, and {@code traceId} is read from the
 * SLF4J {@link MDC} key {@code correlationId} populated by the inbound
 * {@link CorrelationIdFilter} (a fresh UUID is minted only if none is present).
 *
 * <p>No token, header, or key material is ever logged or echoed. The response
 * {@code detail} is a fixed, non-revealing message; it does not include the
 * underlying {@link AuthenticationException} message, which can carry token
 * internals. The body is assembled directly (not via a JSON mapper) because it
 * is a tiny, fixed shape whose only variable fields — the request path and the
 * correlation id — are JSON-escaped here.
 */
public class ProblemAuthenticationEntryPoint implements AuthenticationEntryPoint {

    /** Stable machine-readable code for an unauthenticated request. */
    static final String CODE = "UNAUTHENTICATED";
    /** URN prefix mirrored from {@code GlobalExceptionHandler}. */
    static final String TYPE_PREFIX = "urn:social:error:";
    /** MDC key holding the current correlation id. */
    static final String CORRELATION_MDC_KEY = "correlationId";

    private static final String DETAIL =
        "Authentication is required and the provided credentials are missing or invalid.";

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        String body = "{"
            + "\"type\":\"" + TYPE_PREFIX + CODE + "\","
            + "\"title\":\"Unauthenticated\","
            + "\"status\":" + HttpStatus.UNAUTHORIZED.value() + ","
            + "\"detail\":\"" + DETAIL + "\","
            + "\"instance\":\"" + escape(safeInstance(request)) + "\","
            + "\"code\":\"" + CODE + "\","
            + "\"traceId\":\"" + escape(currentTraceId()) + "\""
            + "}";

        response.getWriter().write(body);
    }

    private static String safeInstance(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri == null || uri.isBlank() ? "/" : uri;
    }

    private static String currentTraceId() {
        String fromContext = MDC.get(CORRELATION_MDC_KEY);
        if (fromContext != null && !fromContext.isBlank()) {
            return fromContext;
        }
        return UUID.randomUUID().toString();
    }

    /** Minimal JSON string escaping for the two variable string fields. */
    private static String escape(String value) {
        StringBuilder sb = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }
}
