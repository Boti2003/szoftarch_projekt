package hu.bme.szoftarch.learning.infrastructure.config;

import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * RestClient bean-ek a kimenő service-hívásokhoz (TERV B – U0).
 *
 * <ul>
 *   <li>{@code defaultRestClient}: connect 2s / read 5s — Identity és egyéb
 *       belső hívásokhoz.</li>
 *   <li>{@code aiRestClient}: connect 2s / read 20s — az AI hívások hosszabb
 *       válaszideje miatt.</li>
 * </ul>
 *
 * Mindkét kliens propagálja az {@code X-Correlation-Id}-t. A konkrét adapterek
 * (IdentityPointsClient, AiClient) később (U5/U8) épülnek ezekre.
 */
@Configuration
public class RestClientConfig {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration AI_READ_TIMEOUT = Duration.ofSeconds(20);

    @Bean
    public RestClient defaultRestClient() {
        return RestClient.builder()
                .requestFactory(requestFactory(DEFAULT_READ_TIMEOUT))
                .requestInterceptor(new CorrelationIdInterceptor())
                .build();
    }

    @Bean
    public RestClient aiRestClient() {
        return RestClient.builder()
                .requestFactory(requestFactory(AI_READ_TIMEOUT))
                .requestInterceptor(new CorrelationIdInterceptor())
                .build();
    }

    private ClientHttpRequestFactory requestFactory(Duration readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT);
        factory.setReadTimeout(readTimeout);
        return factory;
    }
}
