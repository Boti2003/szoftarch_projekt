package hu.bme.szoftarch.social.domain.common;

/**
 * Raised when a retryable mutation is invoked without the required
 * {@code Idempotency-Key} request header.
 *
 * <p>Maps to HTTP 400 with code {@code IDEMPOTENCY_KEY_REQUIRED} (design "Error
 * Handling", requirement 9.1). The API layer rejects the request before any
 * state is mutated so a client can safely retry once it supplies a key.
 *
 * <p>This type lives in {@code domain.common} alongside the other stable error
 * codes so the API layer's {@code GlobalExceptionHandler} (task 10.1) can map it
 * uniformly through the shared {@link DomainException#code()} contract. It is
 * raised by the API-layer idempotency helper
 * ({@code api.error.Idempotency#requireKey(String)}) that the duel/friend
 * mutation controllers (tasks 10.2/10.3) call at the top of each retryable
 * handler.
 */
public final class IdempotencyKeyRequiredException extends DomainException {

    /** Stable error code mapped to HTTP 400 by the API layer. */
    public static final String CODE = "IDEMPOTENCY_KEY_REQUIRED";

    /**
     * @param message a human-readable detail describing the missing header
     */
    public IdempotencyKeyRequiredException(String message) {
        super(CODE, message);
    }
}
