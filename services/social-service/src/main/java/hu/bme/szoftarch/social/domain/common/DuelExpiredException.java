package hu.bme.szoftarch.social.domain.common;

/**
 * Raised when an action is attempted on a duel whose expiry window has
 * elapsed (a PENDING duel past 72h, or an ACCEPTED/IN_PROGRESS duel past its
 * 7-day window).
 *
 * <p>Maps to HTTP 409 with code {@code DUEL_EXPIRED} (design "Error
 * Handling"). Expiry is evaluated lazily and is idempotent: re-evaluating an
 * already-expired duel does not change its state.
 */
public final class DuelExpiredException extends DomainException {

    /** Stable error code mapped to HTTP 409 by the API layer. */
    public static final String CODE = "DUEL_EXPIRED";

    /**
     * @param message a human-readable detail describing the expired action
     */
    public DuelExpiredException(String message) {
        super(CODE, message);
    }
}
