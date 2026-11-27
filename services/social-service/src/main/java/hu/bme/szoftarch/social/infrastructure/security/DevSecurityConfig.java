package hu.bme.szoftarch.social.infrastructure.security;

import java.time.Instant;
import java.util.Map;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.web.SecurityFilterChain;

import com.nimbusds.jwt.JWTParser;

/**
 * <strong>DEVELOPMENT / DEMO ONLY</strong> security wiring, active only under the
 * {@code dev} Spring profile.
 *
 * <p>Unlike the production {@link SecurityConfig} (gated to {@code @Profile("!dev")}),
 * this configuration <em>does not verify token signatures</em>. It still runs an
 * OAuth2 resource server so that, when a bearer token is supplied, an
 * authenticated {@link Jwt} principal is placed in the security context and the
 * api-layer {@code CurrentUser}/{@link AuthenticatedUser} can read the acting
 * user from the {@code sub} claim. This lets the full API be exercised from
 * Swagger UI with any self-made JWT (no Identity / JWKS required): paste a token
 * whose {@code sub} is a user UUID and click Authorize.
 *
 * <p>Authorization is permit-all, so unauthenticated calls to open endpoints
 * (Swagger UI, actuator) still work; endpoints that require the caller identity
 * work as soon as a token carrying a {@code sub} is attached.
 *
 * <p><strong>Never enable the {@code dev} profile in a deployed/production
 * environment:</strong> it accepts unsigned/forged tokens and performs no
 * authentication guarantees. It exists purely for local exploration.
 */
@Configuration
@EnableWebSecurity
@Profile("dev")
public class DevSecurityConfig {

    @Bean
    public SecurityFilterChain devSecurityFilterChain(
            HttpSecurity http, JwtDecoder devJwtDecoder) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // Permit everything: a token is optional, but when present it is
            // decoded (below) so the principal/sub is available to controllers.
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.decoder(devJwtDecoder)));
        return http.build();
    }

    /**
     * A permissive {@link JwtDecoder} that parses the JWT and exposes its claims
     * <em>without verifying the signature, issuer, or expiry</em>. Dev-only.
     */
    @Bean
    public JwtDecoder devJwtDecoder() {
        return token -> {
            try {
                var parsed = JWTParser.parse(token);
                var claims = parsed.getJWTClaimsSet();
                Map<String, Object> headers = parsed.getHeader().toJSONObject();
                Map<String, Object> claimMap = claims.getClaims();

                Instant issuedAt = claims.getIssueTime() != null
                    ? claims.getIssueTime().toInstant() : Instant.now();
                Instant expiresAt = claims.getExpirationTime() != null
                    ? claims.getExpirationTime().toInstant() : Instant.now().plusSeconds(3600);

                return Jwt.withTokenValue(token)
                    .headers(h -> h.putAll(headers))
                    .claims(c -> c.putAll(claimMap))
                    .issuedAt(issuedAt)
                    .expiresAt(expiresAt)
                    .build();
            } catch (Exception ex) {
                throw new JwtException("DEV decoder could not parse token: " + ex.getMessage(), ex);
            }
        };
    }
}
