package hu.bme.szoftarch.social.infrastructure.learning;

/**
 * Raised when an outbound call to the Learning internal API cannot be completed
 * successfully after the retry budget is exhausted, e.g. on a connect/read
 * timeout or a 5xx upstream response.
 *
 * <p>The application layer maps this to a {@code 502/503} problem+json response
 * with code {@code UPSTREAM_UNAVAILABLE} (see design "Error handling"). When
 * this is raised during a duel answer, the caller leaves participant state
 * unchanged (design "Learning failure safety", requirement 5.1). No distributed
 * transaction or compensating write is performed (requirement 12.4).
 *
 * <p>The message never includes the internal API key or any token (requirement
 * 13.2).
 */
public class IntegrationException extends RuntimeException {

    public IntegrationException(String message, Throwable cause) {
        super(message, cause);
    }

    public IntegrationException(String message) {
        super(message);
    }
}
