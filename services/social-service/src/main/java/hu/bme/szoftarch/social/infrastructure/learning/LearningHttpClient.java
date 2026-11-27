package hu.bme.szoftarch.social.infrastructure.learning;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Builds the {@link RestClient} used by {@link LearningDuelAdapter} to call the
 * Learning internal API.
 *
 * <p>The client is configured with a 2s connect timeout and a 5s read timeout
 * (design "Outbound adapter reliability", requirements 12.1/12.2), a base URL
 * from {@code social.learning.base-url}, and a constant
 * {@code X-Internal-Api-Key} default header read from {@code social.learning.api-key}
 * (requirement 12.1). The per-request {@code X-Correlation-Id} header is set by
 * the adapter on each call. The at-most-one retry on timeout/5xx is implemented
 * in the adapter so that only idempotent or Idempotency-Key-carrying calls are
 * retried (requirement 12.3).
 *
 * <p>The API key is never logged; it is only attached as a request header.
 */
@Configuration
@EnableConfigurationProperties(LearningClientProperties.class)
public class LearningHttpClient {

    /** Qualifier so this bean never collides with other service clients. */
    static final String REST_CLIENT_BEAN = "learningRestClient";

    /**
     * Request factory with the mandated connect/read timeouts. Kept as its own
     * bean so timeouts are visible and independently testable.
     */
    @Bean("learningClientRequestFactory")
    ClientHttpRequestFactory learningClientRequestFactory(LearningClientProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) properties.connectTimeout().toMillis());
        factory.setReadTimeout((int) properties.readTimeout().toMillis());
        return factory;
    }

    @Bean(REST_CLIENT_BEAN)
    RestClient learningRestClient(LearningClientProperties properties,
                                  ClientHttpRequestFactory learningClientRequestFactory) {
        RestClient.Builder builder = RestClient.builder()
                .requestFactory(learningClientRequestFactory)
                .baseUrl(properties.baseUrl());
        if (properties.apiKey() != null && !properties.apiKey().isBlank()) {
            builder.defaultHeader("X-Internal-Api-Key", properties.apiKey());
        }
        return builder.build();
    }
}
