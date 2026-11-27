package hu.bme.szoftarch.social.domain.common;

/**
 * Raised when a friend request cannot be created because an active
 * {@code PENDING} request already exists between the two users in either
 * direction (also used for the race on the partial-unique pending index).
 *
 * <p>Maps to HTTP 409 with code {@code REQUEST_EXISTS} (design "Error
 * Handling").
 */
public final class RequestExistsException extends DomainException {

    /** Stable error code mapped to HTTP 409 by the API layer. */
    public static final String CODE = "REQUEST_EXISTS";

    /**
     * @param message a human-readable detail describing the duplicate request
     */
    public RequestExistsException(String message) {
        super(CODE, message);
    }
}
