package hu.bme.szoftarch.social.domain.common;

/**
 * Raised when a user attempts to send a friend request to themselves
 * ({@code sender == receiver}).
 *
 * <p>Maps to HTTP 400 with code {@code SELF_REQUEST} (design "Error
 * Handling").
 */
public final class SelfRequestException extends DomainException {

    /** Stable error code mapped to HTTP 400 by the API layer. */
    public static final String CODE = "SELF_REQUEST";

    /**
     * @param message a human-readable detail describing the self-request
     */
    public SelfRequestException(String message) {
        super(CODE, message);
    }
}
