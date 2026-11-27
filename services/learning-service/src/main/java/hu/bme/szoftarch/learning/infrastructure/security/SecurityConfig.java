package hu.bme.szoftarch.learning.infrastructure.security;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.security.interfaces.RSAPublicKey;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * RS256 JWT resource server a Learning service-hez (TERV 8.).
 *
 * <ul>
 *   <li>A health és az {@code /actuator/**} végpontok publikusak.</li>
 *   <li>Minden más ({@code /api/v1/**}, {@code /internal/v1/**}) hitelesített.</li>
 *   <li>A tokent RS256-tal, a konfigból/env-ből betöltött PUBLIKUS kulccsal
 *       validáljuk; a privát kulcs az Identity service-é.</li>
 *   <li>Az {@code /internal/v1/**} végpontokat ezen felül az
 *       {@link InternalApiKeyFilter} is védi (X-Internal-Api-Key).</li>
 * </ul>
 */
@Configuration
@EnableConfigurationProperties({JwtProperties.class, InternalApiProperties.class})
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, InternalApiProperties internalApiProperties)
            throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers("/api/v1/learning/health").permitAll()
                        // A belső végpontokat NEM JWT, hanem kizárólag az
                        // InternalApiKeyFilter (X-Internal-Api-Key) védi: itt NINCS JWT
                        // user-kontextus (U7 duel-set — a userId a payloadból jön).
                        .requestMatchers("/internal/v1/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                .addFilterBefore(
                        new InternalApiKeyFilter(internalApiProperties.apiKey()),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /**
     * JwtDecoder a publikus RSA kulccsal, kiegészítve kötelező
     * lejárat- és (ha konfigurált) issuer-ellenőrzéssel.
     */
    @Bean
    public JwtDecoder jwtDecoder(JwtProperties jwtProperties) {
        RSAPublicKey publicKey = readPublicKey(jwtProperties);
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(publicKey).build();

        OAuth2TokenValidator<Jwt> validator = (jwtProperties.issuer() == null || jwtProperties.issuer().isBlank())
                ? JwtValidators.createDefault()
                : JwtValidators.createDefaultWithIssuer(jwtProperties.issuer());
        decoder.setJwtValidator(validator);
        return decoder;
    }

    private RSAPublicKey readPublicKey(JwtProperties jwtProperties) {
        if (jwtProperties.publicKey() == null) {
            throw new IllegalStateException(
                    "learning.security.jwt.public-key is not configured");
        }
        try (InputStream in = jwtProperties.publicKey().getInputStream()) {
            return RsaKeyConverters.x509().convert(in);
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to read JWT public key", e);
        }
    }
}
