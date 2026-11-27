package hu.bme.szoftarch.social.infrastructure.identity;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Builds the {@link RestClient} used by {@link IdentityStatsAdapter} to call
 * Identity's internal API.
 *
 * <p>The client is configured with a 2s connect timeout and a 5s read timeout
 * (design "Outbound adapter reliability") and a request interceptor that stamps
 * every outgoing request with the {@code X-Internal-Api-Key} and
 * {@code X-Correlation-Id} headers. The api key is attached here, in one place,
 * and is never written to logs.
 *
 * <p>Retry-on-timeout/5xx for idempotent reads is handled in the adapter, not
 * here, so this config stays a plain transport concern.
 */
@Configuration
@EnableConfigurationProperties(IdentityClientProperties.class)
public class IdentityHttpClientConfig {

    /**
     * Request factory with the mandated connect/read timeouts. A fresh factory
     * is created per client so these timeouts are not shared with other beans.
     */
    @Bean
    ClientHttpRequestFactory identityClientHttpRequestFactory(IdentityClientProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.connectTimeout());
        factory.setReadTimeout(properties.readTimeout());
        return factory;
    }

    /**
     * Interceptor attaching the internal API key and the current correlation id
     * to every request. Only this component sees the key; it is never logged.
     */
    @Bean
    ClientHttpRequestInterceptor identityInternalHeadersInterceptor(
            IdentityClientProperties properties, CorrelationIdAccessor correlationIdAccessor) {
        return (request, body, execution) -> {
            if (properties.apiKey() != null && !properties.apiKey().isBlank()) {
                request.getHeaders().set("X-Internal-Api-Key", properties.apiKey());
            }
            request.getHeaders().set(properties.correlationHeader(), correlationIdAccessor.current());
            return execution.execute(request, body);
        };
    }

    @Bean
    RestClient identityRestClient(
            IdentityClientProperties properties,
            ClientHttpRequestFactory identityClientHttpRequestFactory,
            ClientHttpRequestInterceptor identityInternalHeadersInterceptor) {
        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(identityClientHttpRequestFactory)
                .requestInterceptor(identityInternalHeadersInterceptor)
                .build();
    }
}
