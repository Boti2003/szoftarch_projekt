package hu.bme.szoftarch.learning.infrastructure.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

/**
 * JWT resource server beállítások. A Learning CSAK a publikus kulcsot ismeri
 * (a privát kulcs az Identity service secretje, TERV 8.). Az {@code issuer} és a
 * {@code publicKey} forrása konfigból/env-ből jön.
 */
@ConfigurationProperties(prefix = "learning.security.jwt")
public record JwtProperties(String issuer, Resource publicKey) {
}
