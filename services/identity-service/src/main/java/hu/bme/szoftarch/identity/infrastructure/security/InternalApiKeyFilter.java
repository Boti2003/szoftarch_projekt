package hu.bme.szoftarch.identity.infrastructure.security;

import hu.bme.szoftarch.identity.api.dto.ProblemDetails;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Filter that protects internal endpoints (/internal/**) using the X-Internal-Api-Key header.
 * Uses constant-time comparison (MessageDigest.isEqual).
 * Rejections return RFC 9457 ProblemDetails with HTTP 401.
 */
public class InternalApiKeyFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(InternalApiKeyFilter.class);
    public static final String HEADER_NAME = "X-Internal-Api-Key";

    private final String internalApiKey;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public InternalApiKeyFilter(String internalApiKey) {
        this.internalApiKey = internalApiKey;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String uri = request.getRequestURI();

        if (uri.startsWith("/internal/")) {
            String apiKeyHeader = request.getHeader(HEADER_NAME);

            if (apiKeyHeader == null || !isApiKeyValid(apiKeyHeader)) {
                log.warn("[SECURITY_ALERT: INTERNAL_API_KEY_INVALID] Rejected unauthorized internal API access: uri={}, remoteAddr={}",
                        uri, request.getRemoteAddr());

                response.setStatus(HttpStatus.UNAUTHORIZED.value());
                response.setContentType("application/problem+json");

                ProblemDetails problem = new ProblemDetails(
                        "urn:problem:unauthorized",
                        "Unauthorized",
                        HttpStatus.UNAUTHORIZED.value(),
                        "Missing or invalid internal API key",
                        uri,
                        "INTERNAL_API_KEY_INVALID"
                );

                response.getWriter().write(objectMapper.writeValueAsString(problem));
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean isApiKeyValid(String providedKey) {
        if (internalApiKey == null || internalApiKey.isBlank() || providedKey == null) {
            return false;
        }
        return MessageDigest.isEqual(
                internalApiKey.getBytes(StandardCharsets.UTF_8),
                providedKey.getBytes(StandardCharsets.UTF_8)
        );
    }
}
