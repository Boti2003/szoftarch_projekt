package hu.bme.szoftarch.social.domain.common;

/**
 * Raised when a friend request cannot be created because a friendship already
 * exists between the two users.
 *
 * <p>Maps to HTTP 409 with code {@code ALREADY_FRIENDS} (design "Error
 * Handling").
 */
public final class AlreadyFriendsException extends DomainException {

    /** Stable error code mapped to HTTP 409 by the API layer. */
    public static final String CODE = "ALREADY_FRIENDS";

    /**
     * @param message a human-readable detail describing the existing friendship
     */
    public AlreadyFriendsException(String message) {
        super(CODE, message);
    }
}
