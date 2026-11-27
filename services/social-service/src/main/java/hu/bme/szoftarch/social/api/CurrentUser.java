package hu.bme.szoftarch.social.api;

import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Shared API-layer helper that derives the <em>acting</em> user id from the
 * authenticated JWT's {@code sub} claim (requirement 4.1).
 *
 * <p>Every mutating and read controller in the social API must identify the
 * actor from the verified token, never from a request body or path segment, so
 * a caller can only ever act as themselves. Controllers inject the principal as
 * {@code @AuthenticationPrincipal Jwt jwt} and call {@link #require(Jwt)} as the
 * first step of each handler, then pass the returned id to the use case (whose
 * first parameter is always the {@code sub}-sourced user id).
 *
 * <p><strong>Minimal by design.</strong> The security task (task 11.1) may later
 * introduce a dedicated {@code AuthenticatedUser} principal/bean and swap this
 * helper for it. Until then this is the single place that parses the subject, so
 * the duel, leaderboard, notification, friend, and friend-request controllers
 * (tasks 10.2/10.3) share one consistent extraction. This class is intentionally
 * dependency-light: it holds only the {@code sub}&rarr;UUID parse so the later
 * swap touches one call site per handler.
 *
 * <h2>Subject format</h2>
 * The project issues user ids as UUIDs and places them in the token
 * {@code sub} claim, so {@link #require(Jwt)} parses the subject with
 * {@link UUID#fromString(String)}. A missing or non-UUID subject means the token
 * is not one this service can act on; it is rejected with
 * {@link IllegalStateException} rather than silently defaulting, because a
 * well-formed authenticated token is a precondition established by the resource
 * server before any controller runs (an invalid/expired token is already a 401
 * at the security entry point, per the design error table).
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    /**
     * Extracts the acting user id from the authenticated JWT's {@code sub}
     * claim.
     *
     * @param jwt the authenticated principal; must not be {@code null}
     * @return the acting user id parsed from {@code sub}
     * @throws IllegalStateException if the JWT is absent or its {@code sub}
     *                               claim is missing or not a valid UUID
     */
    public static UUID require(Jwt jwt) {
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
