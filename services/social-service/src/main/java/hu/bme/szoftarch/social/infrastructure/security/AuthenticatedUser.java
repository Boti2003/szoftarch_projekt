package hu.bme.szoftarch.social.infrastructure.security;

import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Accessor that exposes the authenticated user's id (the JWT {@code sub} claim,
 * parsed as a {@link UUID}) to the application layer, per the design interface
 * {@code AuthenticatedUser { UUID id(); }} (requirement 4.1).
 *
 * <p>The id is always taken from the verified token in the current
 * {@link SecurityContextHolder security context}, never from a request body or
 * path variable, so a caller can only ever act as themselves (property 11). The
 * resource server has already validated the token (signature, expiry, RS256)
 * before any code that reads this accessor runs; an invalid or missing token is
 * rejected with 401 at the authentication entry point.
 *
 * <p>This is a thin, stateless component (safe as a singleton: it reads the
 * per-thread security context on each call) introduced <em>alongside</em> the
 * existing {@code hu.bme.szoftarch.social.api.CurrentUser} helper. Controllers
 * that inject {@code @AuthenticationPrincipal Jwt} keep working unchanged;
 * {@code CurrentUser} and this accessor parse the subject identically (UUID of
 * {@code sub}), so either path yields the same actor id. Tokens and claim values
 * are never logged here.
 */
@Component
public class AuthenticatedUser {

    /**
     * Returns the acting user id parsed from the current authentication's JWT
     * {@code sub} claim.
     *
     * @return the authenticated user id
     * @throws IllegalStateException if there is no authenticated JWT in the
     *                               security context, or its {@code sub} claim
     *                               is missing or not a valid UUID
     */
    public UUID id() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
            return parseSubject(jwtAuthentication.getToken());
        }
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            return parseSubject(jwt);
        }
        throw new IllegalStateException("no authenticated JWT principal is present");
    }

    private static UUID parseSubject(Jwt jwt) {
        if (jwt == null) {
            throw new IllegalStateException("no authenticated JWT principal is present");
        }
        String subject = jwt.getSubject();
        if (subject == null || subject.isBlank()) {
            throw new IllegalStateException("authenticated JWT has no 'sub' claim");
        }
        try {
            return UUID.fromString(subject);
        } catch (IllegalArgumentException malformed) {
            throw new IllegalStateException("authenticated JWT 'sub' is not a valid user id", malformed);
        }
    }
}
