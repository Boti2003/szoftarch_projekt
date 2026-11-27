package hu.bme.szoftarch.social.infrastructure.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.SupplierJwtDecoder;
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
 * The {@link JwtDecoder} is built from the standard Boot property
 * {@code spring.security.oauth2.resourceserver.jwt.jwk-set-uri} and pinned to
 * {@code RS256}, so only RS256-signed tokens whose key is published by Identity's
 * JWKS are accepted. The decoder is constructed lazily (it fetches the JWKS on
 * first validation, not at startup) so the application context loads without a
 * live Identity. Standard timestamp validation ({@code exp}/{@code nbf}) plus a
 * non-blank {@code sub} requirement are enforced; an invalid, expired, or
 * missing token is rejected with 401 {@code UNAUTHENTICATED} by
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

    private final String jwkSetUri;

    public SecurityConfig(
            @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwkSetUri) {
        this.jwkSetUri = jwkSetUri;
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
     * RS256-only {@link JwtDecoder} backed by Identity's JWKS. Built lazily via a
     * {@link SupplierJwtDecoder} so the JWKS is not fetched until the first token
     * is validated, letting the context start without a reachable Identity.
     */
    @Bean
    public JwtDecoder jwtDecoder() {
        return new SupplierJwtDecoder(this::buildDecoder);
    }

    private JwtDecoder buildDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
            .withJwkSetUri(jwkSetUri)
            .jwsAlgorithm(SignatureAlgorithm.RS256)
            .build();
        OAuth2TokenValidator<Jwt> defaults = JwtValidators.createDefault();
        OAuth2TokenValidator<Jwt> hasSubject = new JwtClaimValidator<String>("sub",
            subject -> subject != null && !subject.isBlank());
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(defaults, hasSubject));
        return decoder;
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
