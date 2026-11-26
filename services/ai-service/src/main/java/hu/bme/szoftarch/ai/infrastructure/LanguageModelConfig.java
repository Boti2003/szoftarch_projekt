package hu.bme.szoftarch.ai.infrastructure;

import hu.bme.szoftarch.ai.domain.LanguageModelClient;
import hu.bme.szoftarch.ai.infrastructure.openai.OpenAiLanguageModelClient;
import hu.bme.szoftarch.ai.infrastructure.openai.OpenAiProperties;
import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;

/**
 * A futásidejű nyelvi modell provider kiválasztása az {@code AI_PROVIDER}
 * környezeti változó alapján. A domain és application réteg csak a
 * {@link LanguageModelClient} portot látja, a konkrét adaptert nem.
 */
@Configuration
@EnableConfigurationProperties(OpenAiProperties.class)
public class LanguageModelConfig {

    @Bean
    public LanguageModelClient languageModelClient(
            @Value("${ai.provider:fake}") String provider,
            OpenAiProperties openAiProperties,
            ObjectMapper objectMapper,
            Validator validator) {
        return switch (provider.toLowerCase()) {
            case "fake" -> new FakeLanguageModelClient();
            case "openai-compatible" -> new OpenAiLanguageModelClient(
                    buildRestClient(openAiProperties), objectMapper, validator, openAiProperties.model());
            default -> throw new IllegalStateException("Ismeretlen AI_PROVIDER: " + provider);
        };
    }

    private RestClient buildRestClient(OpenAiProperties props) {
        if (props.apiKey() == null || props.apiKey().isBlank()) {
            throw new IllegalStateException(
                    "Az openai-compatible provider AI_OPENAI_API_KEY nélkül nem használható.");
        }
        Duration timeout = Duration.ofSeconds(props.requestTimeoutSeconds());
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(2));
        requestFactory.setReadTimeout(timeout);

        return RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl(props.baseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + props.apiKey())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}
