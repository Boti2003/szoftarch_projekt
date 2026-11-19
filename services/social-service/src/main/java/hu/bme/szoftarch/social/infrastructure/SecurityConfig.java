package hu.bme.szoftarch.social.infrastructure;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * SKELETON security: egyelőre minden kérés engedélyezett, hogy a health
 * dashboard end-to-end működjön. A valódi JWT (RS256) resource server
 * konfigurációt a service gazdája építi a saját branchén (8. szakasz).
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
