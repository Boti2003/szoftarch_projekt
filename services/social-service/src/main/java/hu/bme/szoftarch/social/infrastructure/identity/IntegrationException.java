package hu.bme.szoftarch.social.infrastructure.identity;

/**
 * Raised when an outbound call to an internal service (here: Identity) cannot
 * be completed successfully after the retry budget is exhausted, e.g. on a
 * connect/read timeout or a 5xx upstream response.
 *
 * <p>The application layer maps this to a {@code 502/503} problem+json response
 * with code {@code UPSTREAM_UNAVAILABLE} (see design "Error handling"). The
 * message never includes the internal API key or any token.
 */
public class IntegrationException extends RuntimeException {

    public IntegrationException(String message, Throwable cause) {
        super(message, cause);
    }

    public IntegrationException(String message) {
        super(message);
    }
}
