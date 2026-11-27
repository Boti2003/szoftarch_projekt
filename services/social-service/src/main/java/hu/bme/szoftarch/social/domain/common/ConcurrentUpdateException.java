package hu.bme.szoftarch.social.domain.common;

/**
 * Raised when a concurrent write loses an optimistic-lock check — notably a
 * conflicting update to {@code duel_participants.version} during answer
 * submission.
 *
 * <p>Maps to HTTP 409 with code {@code CONCURRENT_UPDATE} (design "Error
 * Handling"). The client may safely retry with the same Idempotency-Key.
 */
public final class ConcurrentUpdateException extends DomainException {

    /** Stable error code mapped to HTTP 409 by the API layer. */
    public static final String CODE = "CONCURRENT_UPDATE";

    /**
     * @param message a human-readable detail describing the conflict
     */
    public ConcurrentUpdateException(String message) {
        super(CODE, message);
    }
}
