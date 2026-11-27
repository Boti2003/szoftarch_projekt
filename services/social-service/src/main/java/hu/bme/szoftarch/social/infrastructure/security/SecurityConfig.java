package hu.bme.szoftarch.social.infrastructure.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.security.converter.RsaKeyConverters;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.security.interfaces.RSAPublicKey;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

/**
 * Security wiring for the Social Service: an OAuth2 <strong>resource server</strong>
 * that validates RS256 access tokens against Identity's JWKS endpoint and
 * protects every public API route (requirements 4.1, 4.2, 4.3).
 *
 * <p>This replaces the previous permissive {@code permitAll} skeleton. There is
 * exactly one {@link SecurityFilterChain} bean in the application.
 *
 * <h2>Authorization rules</h2>
 * <ul>
 *   <li>Actuator health and info ({@code /actuator/health/**}, {@code /actuator/info})
 *       are open so container/orchestrator probes and the health dashboard keep
 *       working.</li>
 *   <li>The framework error dispatch path ({@code /error}) is permitted so error
 *       rendering is never itself blocked by authentication.</li>
 *   <li>Everything else — notably all {@code /api/v1/**} — requires an
 *       authenticated JWT.</li>
 * </ul>
 *
 * <h2>Token validation</h2>
 * The {@link JwtDecoder} validates RS256 tokens with the shared PUBLIC key
 * loaded from {@code social.security.jwt.public-key} (an X.509 PEM mounted from
 * the project secrets, default {@code file:/run/secrets/jwt-public.pem}). This
 * matches how the other services validate tokens; Identity holds the private
 * key. Standard timestamp validation ({@code exp}/{@code nbf}) plus a non-blank
 * {@code sub} requirement are enforced; an invalid, expired, or missing token is
 * rejected with 401 {@code UNAUTHENTICATED} by
 * {@link ProblemAuthenticationEntryPoint}.
 *
 * <h2>Principal / authorities</h2>
 * Authorities are derived by {@link JwtAuthoritiesConverter}; the acting user id
 * is always the token {@code sub} (exposed via {@link AuthenticatedUser} and the
 * api-layer {@code CurrentUser} helper), never a request body or path value.
 */
@Configuration
@EnableWebSecurity
@Profile("!dev") // The RS256 resource server is active in every profile except "dev".
public class SecurityConfig {

    private static final String[] PUBLIC_PATHS = {
        "/actuator/health/**",
        "/actuator/info",
        "/error",
        // OpenAPI description + Swagger UI (springdoc). Dev/demo convenience:
        // these expose the API schema and an interactive console, so keep them
        // behind the network boundary (not routed through nginx) in production.
        "/v3/api-docs/**",
        "/swagger-ui/**",
        "/swagger-ui.html"
    };

    private final Resource publicKey;
    private final String issuer;

    public SecurityConfig(
            @Value("${social.security.jwt.public-key:file:/run/secrets/jwt-public.pem}") Resource publicKey,
            @Value("${social.security.jwt.issuer:}") String issuer) {
        this.publicKey = publicKey;
        this.issuer = issuer;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtDecoder jwtDecoder,
            JwtAuthenticationConverter jwtAuthenticationConverter,
            AuthenticationEntryPoint problemAuthenticationEntryPoint) throws Exception {
        http
            // Stateless API: no HTTP session, no CSRF token handling.
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(PUBLIC_PATHS).permitAll()
                .anyRequest().authenticated())
            .oauth2ResourceServer(oauth2 -> oauth2
                .authenticationEntryPoint(problemAuthenticationEntryPoint)
                .jwt(jwt -> jwt
                    .decoder(jwtDecoder)
                    .jwtAuthenticationConverter(jwtAuthenticationConverter)))
            .exceptionHandling(ex -> ex
                // 401 for unauthenticated requests -> problem+json UNAUTHENTICATED.
                .authenticationEntryPoint(problemAuthenticationEntryPoint)
                // 403 (access rules) stays minimal here; domain 403s flow through
                // GlobalExceptionHandler as FORBIDDEN.
                .accessDeniedHandler((request, response, denied) ->
                    new HttpStatusEntryPoint(HttpStatus.FORBIDDEN)
                        .commence(request, response, null)));
        return http.build();
    }

    /**
     * RS256 {@link JwtDecoder} using the shared public key (X.509 PEM). The
     * matching private key is the Identity service secret. Enforces default
     * timestamp validation (plus issuer when configured) and a non-blank
     * {@code sub}.
     */
    @Bean
    public JwtDecoder jwtDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(readPublicKey()).build();
        OAuth2TokenValidator<Jwt> timestamps = (issuer == null || issuer.isBlank())
            ? JwtValidators.createDefault()
            : JwtValidators.createDefaultWithIssuer(issuer);
        OAuth2TokenValidator<Jwt> hasSubject = new JwtClaimValidator<String>("sub",
            subject -> subject != null && !subject.isBlank());
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(timestamps, hasSubject));
        return decoder;
    }

    private RSAPublicKey readPublicKey() {
        if (publicKey == null) {
            throw new IllegalStateException("social.security.jwt.public-key is not configured");
        }
        try (InputStream in = publicKey.getInputStream()) {
            return (RSAPublicKey) RsaKeyConverters.x509().convert(in);
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to read JWT public key", e);
        }
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new JwtAuthoritiesConverter());
        return converter;
    }

    @Bean
    public AuthenticationEntryPoint problemAuthenticationEntryPoint() {
        return new ProblemAuthenticationEntryPoint();
    }
}
