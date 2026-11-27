package hu.bme.szoftarch.social.domain.common;

/**
 * Raised when a required outbound dependency (Identity or Learning) cannot be
 * reached successfully after the adapter's retry budget is exhausted — a
 * connect/read timeout or a 5xx upstream response.
 *
 * <p>Maps to HTTP 502/503 with code {@code UPSTREAM_UNAVAILABLE} (design "Error
 * Handling"). It is a transient failure: the client may safely retry with the
 * same Idempotency-Key, because the authoritative scoring performed by Learning
 * is idempotent.
 *
 * <p>This type lives in {@code domain.common} alongside the other stable error
 * codes so the API layer's {@code GlobalExceptionHandler} (task 10.1) can map it
 * uniformly. The application layer translates an adapter-level integration
 * failure (the outbound port's documented failure mode) into this domain
 * exception, so no infrastructure exception type leaks past the API boundary.
 * When this is raised during a duel answer, the caller has made no state
 * mutation — participant {@code score}, {@code answeredCount}, and
 * {@code status} are left unchanged (design "Learning failure safety",
 * requirement 5.1). The message never includes tokens, internal API keys, or
 * correct answers (requirement 13.2).
 */
public final class UpstreamUnavailableException extends DomainException {

    /** Stable error code mapped to HTTP 502/503 by the API layer. */
    public static final String CODE = "UPSTREAM_UNAVAILABLE";

    /**
     * @param message a human-readable detail describing the upstream failure
     */
    public UpstreamUnavailableException(String message) {
        super(CODE, message);
    }

    /**
     * @param message a human-readable detail describing the upstream failure
     * @param cause   the originating adapter integration failure
     */
    public UpstreamUnavailableException(String message, Throwable cause) {
        super(CODE, message);
        initCause(cause);
    }
}
