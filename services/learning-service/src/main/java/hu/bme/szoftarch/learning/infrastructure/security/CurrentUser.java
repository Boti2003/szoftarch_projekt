package hu.bme.szoftarch.learning.infrastructure.security;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * A hitelesített felhasználó azonosítóját KIZÁRÓLAG a JWT {@code sub} claimjéből
 * nyeri ki, soha nem request bodyból (TERV 7.). A {@code sub} UUID formátumú
 * (Identity felhasználó-azonosító).
 */
@Component
public class CurrentUser {

    /**
     * @return az aktuális kérés hitelesített felhasználójának azonosítója
     * @throws IllegalStateException ha nincs JWT alapú hitelesítés a kontextusban
     */
    public UUID requireUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new IllegalStateException("No authenticated JWT principal in security context");
        }
        String subject = jwt.getSubject();
        if (subject == null || subject.isBlank()) {
            throw new IllegalStateException("JWT 'sub' claim is missing");
        }
        return UUID.fromString(subject);
    }
}
