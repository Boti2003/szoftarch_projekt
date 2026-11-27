package hu.bme.szoftarch.social.domain.port;

import java.util.UUID;

/**
 * Outbound port used to check whether a user exists in the Identity service.
 *
 * <p>Implemented by an infrastructure adapter backed by Identity's internal
 * API ({@code GET /internal/v1/users/{id}/exists}). This is a pure domain
 * contract with no Spring or JPA dependency; integration concerns (headers,
 * timeouts, retries) live in the adapter.
 */
public interface UserExistencePort {

    /**
     * Returns whether a user with the given id exists.
     *
     * @param userId the user id to check; must not be {@code null}
     * @return {@code true} if the user exists, {@code false} otherwise
     */
    boolean exists(UUID userId);
}
