package hu.bme.szoftarch.social.infrastructure.learning;

/**
 * Raised when Learning rejects a call with a 4xx <em>client</em> error (e.g. a
 * malformed or type-mismatched duel answer). Unlike the retryable transport/5xx
 * {@link IntegrationException}, this signals that the request itself was invalid,
 * so the orchestrator surfaces it to the caller as a 400 rather than a 502/503.
 */
public class IntegrationClientException extends IntegrationException {

    public IntegrationClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
