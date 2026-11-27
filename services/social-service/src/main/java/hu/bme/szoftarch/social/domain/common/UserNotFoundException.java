package hu.bme.szoftarch.social.domain.common;

/**
 * Raised when a referenced user does not exist — for example, a friend
 * request targets a receiver that Identity reports as non-existent.
 *
 * <p>Maps to HTTP 404 with code {@code USER_NOT_FOUND} (design "Error
 * Handling").
 */
public final class UserNotFoundException extends DomainException {

    /** Stable error code mapped to HTTP 404 by the API layer. */
    public static final String CODE = "USER_NOT_FOUND";

    /**
     * @param message a human-readable detail describing the missing user
     */
    public UserNotFoundException(String message) {
        super(CODE, message);
    }
}
