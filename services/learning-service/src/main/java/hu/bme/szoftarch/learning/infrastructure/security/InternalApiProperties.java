package hu.bme.szoftarch.learning.infrastructure.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * A service-to-service hívások közös kulcsa (X-Internal-Api-Key, TERV 8.).
 * Az érték kizárólag környezeti secret ({@code INTERNAL_API_KEY}), soha nem
 * kerül logba.
 */
@ConfigurationProperties(prefix = "learning.security.internal")
public record InternalApiProperties(String apiKey) {
}
