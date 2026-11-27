package hu.bme.szoftarch.social.infrastructure.identity;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuration for the Identity internal-API client.
 *
 * <p>Bound from the {@code social.identity.*} and {@code social.internal.*}
 * namespaces in {@code application.yml}. The API key is a secret and is never
 * logged (do not add it to {@code toString} / structured logs).
 *
 * @param baseUrl              base URL of the Identity internal API, e.g.
 *                             {@code http://identity:8081/internal/v1}
 * @param apiKey               shared internal API key sent as
 *                             {@code X-Internal-Api-Key} (secret; never logged)
 * @param correlationHeader    name of the correlation-id header to propagate
 * @param connectTimeout       TCP connect timeout (default 2s)
 * @param readTimeout          socket read timeout (default 5s)
 * @param batchSize            max user ids per public-stats batch call (&le; 100)
 */
@ConfigurationProperties(prefix = "social.identity")
public record IdentityClientProperties(
        String baseUrl,
        String apiKey,
        String correlationHeader,
        Duration connectTimeout,
        Duration readTimeout,
        Integer batchSize) {

    /** Hard upper bound on batch size mandated by the Identity contract. */
    public static final int MAX_BATCH_SIZE = 100;

    public IdentityClientProperties {
        if (correlationHeader == null || correlationHeader.isBlank()) {
            correlationHeader = "X-Correlation-Id";
        }
        if (connectTimeout == null) {
            connectTimeout = Duration.ofSeconds(2);
        }
        if (readTimeout == null) {
            readTimeout = Duration.ofSeconds(5);
        }
        if (batchSize == null || batchSize <= 0 || batchSize > MAX_BATCH_SIZE) {
            batchSize = MAX_BATCH_SIZE;
        }
    }

    /** Redacting {@code toString} so the api key never reaches logs. */
    @Override
    public String toString() {
        return "IdentityClientProperties[baseUrl=%s, correlationHeader=%s, connectTimeout=%s, readTimeout=%s, batchSize=%d, apiKey=***]"
                .formatted(baseUrl, correlationHeader, connectTimeout, readTimeout, batchSize);
    }
}
