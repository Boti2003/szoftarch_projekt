package hu.bme.szoftarch.social.domain.common;

/**
 * Raised when the acting user (the JWT {@code sub}) is not authorized to
 * perform the requested action — for example, a user who is neither
 * participant acting on a duel, or a non-opponent attempting to accept or
 * reject a challenge.
 *
 * <p>Maps to HTTP 403 with code {@code FORBIDDEN} (design "Error Handling").
 */
public final class ForbiddenException extends DomainException {

    /** Stable error code mapped to HTTP 403 by the API layer. */
    public static final String CODE = "FORBIDDEN";

    /**
     * @param message a human-readable detail describing the authorization
     *                failure (must not disclose the resource's private state)
     */
    public ForbiddenException(String message) {
        super(CODE, message);
    }
}
