package hu.bme.szoftarch.social.domain.common;

/**
 * Raised when an event is illegal for an entity's current state — notably a
 * duel state-machine event that does not match any legal edge for the current
 * {@code DuelStatus}.
 *
 * <p>Maps to HTTP 409 with code {@code INVALID_STATE} (design "Error
 * Handling"). The entity's state is left unchanged when this is raised.
 */
public final class InvalidStateException extends DomainException {

    /** Stable error code mapped to HTTP 409 by the API layer. */
    public static final String CODE = "INVALID_STATE";

    /**
     * @param message a human-readable detail describing the illegal transition
     */
    public InvalidStateException(String message) {
        super(CODE, message);
    }
}
