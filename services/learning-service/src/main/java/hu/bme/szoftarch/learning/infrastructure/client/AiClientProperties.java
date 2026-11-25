package hu.bme.szoftarch.learning.infrastructure.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Az AI belső API elérése (TERV 7.7). A {@code url} a compose-ból/env-ből jön
 * ({@code AI_URL}, pl. {@code http://ai-service:8080}).
 *
 * @param url az AI service bázis URL-je (séma nélküli útvonal nélkül)
 */
@ConfigurationProperties(prefix = "learning.ai")
public record AiClientProperties(String url) {
}
