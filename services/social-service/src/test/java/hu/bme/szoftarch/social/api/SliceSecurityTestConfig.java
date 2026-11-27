package hu.bme.szoftarch.social.api;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Minimal security wiring shared by the API controller MVC slice tests
 * (task 10.4).
 *
 * <h2>Why this exists — isolation from the security task (11.1)</h2>
 * Task 11.1 rewrites the production {@code SecurityConfig} into an RS256 OAuth2
 * resource server whose {@code SecurityFilterChain} needs a {@code JwtDecoder}
 * built from a JWKS URI (and the {@code
 * spring.security.oauth2.resourceserver.jwt.jwk-set-uri} property). Loading that
 * chain inside a {@code @WebMvcTest} would require a live JWKS and couple these
 * tests to whatever shape 11.1 settles on. Each slice therefore excludes the
 * real {@code SecurityConfig} and Boot's resource-server auto-configuration and
 * imports this permissive chain instead.
 *
 * <h2>Why a real chain rather than {@code addFilters = false}</h2>
 * The controllers read the actor via {@code @AuthenticationPrincipal Jwt}. That
 * argument is only resolved when Spring Security's web integration is active; if
 * the filter chain is disabled, Spring MVC instead tries to data-bind {@code Jwt}
 * as a model attribute and fails. Keeping a (permissive) chain in place lets the
 * {@code jwt()} request post-processor populate the security context so the
 * {@code sub}-derived actor id reaches each handler, which is exactly what the
 * Property 11 assertions verify. Authentication/authorization behavior itself
 * (401, open actuator) is out of scope here and is covered by task 11.2.
 */
@TestConfiguration(proxyBeanMethods = false)
@EnableWebSecurity
public class SliceSecurityTestConfig {

    @Bean
    SecurityFilterChain sliceSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
