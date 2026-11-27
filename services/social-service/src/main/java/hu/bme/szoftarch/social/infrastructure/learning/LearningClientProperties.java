package hu.bme.szoftarch.social.infrastructure.learning;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuration for the Learning internal-API client.
 *
 * <p>Bound from the {@code social.learning.*} namespace in
 * {@code application.yml}. The shared internal API key is read from the
 * {@code social.learning.api-key} property, which defaults to the shared
 * {@code social.internal.api-key} value via a placeholder in
 * {@code application.yml}. The API key is a secret and is never logged (it is
 * excluded from {@code toString}).
 *
 * @param baseUrl           base URL of the Learning internal API, e.g.
 *                          {@code http://learning-service:8080/internal/v1}
 * @param apiKey            shared internal API key sent as
 *                          {@code X-Internal-Api-Key} (secret; never logged)
 * @param correlationHeader name of the correlation-id header to propagate
 *                          (defaults to {@code X-Correlation-Id})
 * @param connectTimeout    TCP connect timeout (default 2s)
 * @param readTimeout       socket read timeout (default 5s)
 */
@ConfigurationProperties(prefix = "social.learning")
public record LearningClientProperties(
        String baseUrl,
        String apiKey,
        String correlationHeader,
        Duration connectTimeout,
        Duration readTimeout) {

    public LearningClientProperties {
        if (correlationHeader == null || correlationHeader.isBlank()) {
            correlationHeader = "X-Correlation-Id";
        }
        if (connectTimeout == null) {
            connectTimeout = Duration.ofSeconds(2);
        }
        if (readTimeout == null) {
            readTimeout = Duration.ofSeconds(5);
        }
    }

    /** Redacting {@code toString} so the api key never reaches logs. */
    @Override
    public String toString() {
        return "LearningClientProperties[baseUrl=%s, correlationHeader=%s, connectTimeout=%s, readTimeout=%s, apiKey=***]"
                .formatted(baseUrl, correlationHeader, connectTimeout, readTimeout);
    }
}
