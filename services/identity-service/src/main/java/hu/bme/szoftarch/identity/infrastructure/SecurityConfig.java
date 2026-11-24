package hu.bme.szoftarch.identity.infrastructure;

import hu.bme.szoftarch.identity.api.dto.ProblemDetails;
import hu.bme.szoftarch.identity.infrastructure.security.InternalApiKeyFilter;
import hu.bme.szoftarch.identity.infrastructure.security.JwtProperties;
import hu.bme.szoftarch.identity.infrastructure.security.RsaKeyLoader;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
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
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Path;
import java.security.interfaces.RSAPublicKey;

@Configuration
public class SecurityConfig {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${internal.api-key}")
    private String internalApiKey;

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
            .addFilterBefore(new InternalApiKeyFilter(internalApiKey), UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/auth/**", "/internal/**", "/actuator/**", "/error").permitAll()
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
        return (request, response, authException) -> writeProblem(
                response, request, HttpStatus.UNAUTHORIZED, "unauthorized", "Unauthorized", "UNAUTHORIZED",
                "Full authentication is required to access this resource", authException
        );
    }

    private AccessDeniedHandler problemDetailsAccessDeniedHandler() {
        return (request, response, accessDeniedException) -> writeProblem(
                response, request, HttpStatus.FORBIDDEN, "forbidden", "Forbidden", "FORBIDDEN",
                "Access is denied", accessDeniedException
        );
    }

    private void writeProblem(
            HttpServletResponse response,
            HttpServletRequest request,
            HttpStatus status,
            String typeSuffix,
            String title,
            String code,
            String defaultDetail,
            Exception ex
    ) throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/problem+json");
        String detail = (ex != null && ex.getMessage() != null) ? ex.getMessage() : defaultDetail;
        ProblemDetails problem = new ProblemDetails(
                "urn:problem:" + typeSuffix,
                title,
                status.value(),
                detail,
                request.getRequestURI(),
                code
        );
        response.getWriter().write(objectMapper.writeValueAsString(problem));
    }
}
