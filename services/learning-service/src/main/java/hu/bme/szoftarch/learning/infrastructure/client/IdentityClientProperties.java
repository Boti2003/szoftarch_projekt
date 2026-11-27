package hu.bme.szoftarch.learning.infrastructure.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Az Identity belső API elérése (TERV 7.3). A {@code url} a compose-ból/env-ből
 * jön ({@code IDENTITY_URL}, pl. {@code http://identity-service:8080}).
 *
 * @param url az Identity service bázis URL-je (séma nélküli útvonal nélkül)
 */
@ConfigurationProperties(prefix = "learning.identity")
public record IdentityClientProperties(String url) {
}
