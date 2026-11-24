package hu.bme.szoftarch.learning.infrastructure.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Az {@code /internal/v1/**} végpontokat a hívó service X-Internal-Api-Key
 * headere védi (TERV 8.). Az összehasonlítás konstans idejű, hogy ne szivárogjon
 * időzítési információ; a kulcs értéke soha nem kerül logba vagy a válaszba.
 * A konkrét belső endpointok később (U5/U7) jönnek — ez a mechanizmus helye.
 */
public class InternalApiKeyFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Internal-Api-Key";
    private static final String INTERNAL_PREFIX = "/internal/";

    private final byte[] expectedKey;

    public InternalApiKeyFilter(String expectedKey) {
        this.expectedKey = expectedKey == null
                ? new byte[0]
                : expectedKey.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(INTERNAL_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String provided = request.getHeader(HEADER);
        if (!matches(provided)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean matches(String provided) {
        if (provided == null || expectedKey.length == 0) {
            return false;
        }
        byte[] providedBytes = provided.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expectedKey, providedBytes);
    }
}
