package hu.bme.szoftarch.social.api.error;

import hu.bme.szoftarch.social.domain.common.IdempotencyKeyRequiredException;

import java.util.UUID;

/**
 * API-layer helper that enforces the {@code Idempotency-Key} header on
 * retryable mutations (requirement 9.1).
 *
 * <p><strong>Integration point for tasks 10.2/10.3.</strong> A controller that
 * handles a retryable mutation (send/accept/reject friend request, create duel,
 * accept/reject duel, submit duel answer) declares the header as
 * {@code @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey}
 * and calls {@link #requireKey(String)} as the first statement of the handler,
 * before touching any use case. When the header is absent or blank this throws
 * {@link IdempotencyKeyRequiredException}, which {@code GlobalExceptionHandler}
 * renders as a 400 {@code application/problem+json} with code
 * {@code IDEMPOTENCY_KEY_REQUIRED}. The returned non-blank key is then forwarded
 * to the use case / outbound Learning call.
 *
 * <p>Declaring the header as {@code required = false} (rather than letting
 * Spring raise a {@code MissingRequestHeaderException}) keeps the rejection on
 * the project's stable domain code path so the problem body matches the design
 * error table exactly.
 */
public final class Idempotency {

    /** The header name callers must supply on retryable mutations. */
    public static final String HEADER = "Idempotency-Key";

    private Idempotency() {
    }

    /**
     * Validates that a retryable mutation carried an {@code Idempotency-Key},
     * returning it for forwarding to the use case.
     *
     * @param headerValue the raw header value, or {@code null} when absent
     * @return the validated, non-blank key
     * @throws IdempotencyKeyRequiredException if the header is {@code null} or blank
     */
    public static String requireKey(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            throw new IdempotencyKeyRequiredException(
                    "The '" + HEADER + "' header is required for this request.");
        }
        return headerValue;
    }

    /**
     * Validates the {@code Idempotency-Key} and parses it as a {@link UUID}, for
     * the duel-answer mutation whose use case
     * ({@code DuelAnswerOrchestrator#submitAnswer}) forwards a {@code UUID} key
     * to Learning so scoring is at-most-once on retry (requirement 5.2).
     *
     * <p>A present-but-malformed key (not a valid UUID) is a bad request value,
     * not a success: it is rejected with {@link IdempotencyKeyRequiredException}
     * (the only idempotency-specific 400 code), which
     * {@code GlobalExceptionHandler} renders as a 400
     * {@code application/problem+json} with code {@code IDEMPOTENCY_KEY_REQUIRED}.
     * Reusing that code keeps the rejection on the project's stable domain-code
     * path rather than letting a raw {@link IllegalArgumentException} fall
     * through to the generic 500 handler. The detail message states the key must
     * be a UUID so the client can correct and retry.
     *
     * @param headerValue the raw header value, or {@code null} when absent
     * @return the validated key parsed as a {@link UUID}
     * @throws IdempotencyKeyRequiredException if the header is {@code null},
     *                                         blank, or not a valid UUID (400)
     */
    public static UUID requireKeyAsUuid(String headerValue) {
        String key = requireKey(headerValue);
        try {
            return UUID.fromString(key);
        } catch (IllegalArgumentException malformed) {
            throw new IdempotencyKeyRequiredException(
                    "The '" + HEADER + "' header must be a valid UUID for this request.");
        }
    }
}
