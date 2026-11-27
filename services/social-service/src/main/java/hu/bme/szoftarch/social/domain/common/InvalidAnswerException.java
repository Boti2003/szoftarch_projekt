package hu.bme.szoftarch.social.domain.common;

/**
 * Raised when a submitted duel answer is rejected by Learning as invalid for the
 * exercise (e.g. a type mismatch or malformed payload).
 *
 * <p>Maps to HTTP 400 with code {@code INVALID_ANSWER}. This is a client error
 * (the answer is wrong-shaped), distinct from {@link UpstreamUnavailableException}
 * (502/503) which means Learning was unreachable or failing.
 */
public final class InvalidAnswerException extends DomainException {

    public static final String CODE = "INVALID_ANSWER";

    public InvalidAnswerException(String message) {
        super(CODE, message);
    }
}
