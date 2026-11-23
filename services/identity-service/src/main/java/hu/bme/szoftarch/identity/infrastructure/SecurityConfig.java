package hu.bme.szoftarch.identity.infrastructure;

import hu.bme.szoftarch.identity.api.dto.ProblemDetails;
import hu.bme.szoftarch.identity.infrastructure.security.JwtProperties;
import hu.bme.szoftarch.identity.infrastructure.security.RsaKeyLoader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.security.interfaces.RSAPublicKey;

@Configuration
public class SecurityConfig {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public JwtDecoder jwtDecoder(JwtProperties jwtProperties) throws Exception {
        Path pubPath = RsaKeyLoader.resolvePath(jwtProperties.getPublicKeyPath());
        RSAPublicKey publicKey = RsaKeyLoader.loadPublicKey(pubPath);
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(publicKey).build();
        OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(jwtProperties.getIssuer());
        decoder.setJwtValidator(withIssuer);
        return decoder;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtDecoder jwtDecoder) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/auth/**", "/actuator/**", "/error").permitAll()
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.decoder(jwtDecoder))
                .authenticationEntryPoint(problemDetailsAuthenticationEntryPoint())
                .accessDeniedHandler(problemDetailsAccessDeniedHandler())
            )
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(problemDetailsAuthenticationEntryPoint())
                .accessDeniedHandler(problemDetailsAccessDeniedHandler())
            );
        return http.build();
    }

    private AuthenticationEntryPoint problemDetailsAuthenticationEntryPoint() {
        return (request, response, authException) -> {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType("application/problem+json");
            ProblemDetails problem = new ProblemDetails(
                    "urn:problem:unauthorized",
                    "Unauthorized",
                    HttpStatus.UNAUTHORIZED.value(),
                    authException.getMessage() != null ? authException.getMessage() : "Full authentication is required to access this resource",
                    request.getRequestURI(),
                    "UNAUTHORIZED"
            );
            response.getWriter().write(objectMapper.writeValueAsString(problem));
        };
    }

    private AccessDeniedHandler problemDetailsAccessDeniedHandler() {
        return (request, response, accessDeniedException) -> {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType("application/problem+json");
            ProblemDetails problem = new ProblemDetails(
                    "urn:problem:forbidden",
                    "Forbidden",
                    HttpStatus.FORBIDDEN.value(),
                    accessDeniedException.getMessage() != null ? accessDeniedException.getMessage() : "Access is denied",
                    request.getRequestURI(),
                    "FORBIDDEN"
            );
            response.getWriter().write(objectMapper.writeValueAsString(problem));
        };
    }
}
