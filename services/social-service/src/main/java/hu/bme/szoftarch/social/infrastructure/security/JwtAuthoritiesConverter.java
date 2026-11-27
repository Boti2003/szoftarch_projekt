package hu.bme.szoftarch.social.infrastructure.security;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Maps the claims of a validated access token to Spring Security
 * {@link GrantedAuthority granted authorities} (requirement 4.3).
 *
 * <p>Identity's token issuance is not yet finalized, so this converter is
 * deliberately defensive and understands the two conventional ways an OAuth2
 * authorization server conveys privileges, taking whichever claims are present:
 * <ul>
 *   <li><strong>Scopes</strong>: the standard {@code scope} claim (space-delimited
 *       string, per RFC 6749/8693) or its abbreviated {@code scp} variant (string
 *       or array). Each scope becomes a {@code SCOPE_<value>} authority, matching
 *       Spring's own {@code JwtGrantedAuthoritiesConverter} default so method- and
 *       URL-level {@code hasAuthority("SCOPE_...")} checks work out of the box.</li>
 *   <li><strong>Roles</strong>: a {@code roles} claim (string or array). Each role
 *       becomes a {@code ROLE_<value>} authority so {@code hasRole(...)} checks
 *       work.</li>
 * </ul>
 *
 * <p>The converter never fails on a missing, blank, or unexpectedly-typed claim:
 * absent privilege claims simply yield an empty authority set. Authentication
 * (a valid {@code sub}) is what the filter chain requires for {@code /api/v1/**};
 * authorities are additive and used only where a route or method opts into a
 * finer-grained check. Claim values are never logged here.
 */
public final class JwtAuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    /** Standard OAuth2 scope claim (space-delimited string). */
    static final String SCOPE_CLAIM = "scope";
    /** Abbreviated scope claim (string or array). */
    static final String SCP_CLAIM = "scp";
    /** Roles claim (string or array). */
    static final String ROLES_CLAIM = "roles";

    static final String SCOPE_PREFIX = "SCOPE_";
    static final String ROLE_PREFIX = "ROLE_";

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Set<GrantedAuthority> authorities = new LinkedHashSet<>();
        for (String scope : scopes(jwt)) {
            authorities.add(new SimpleGrantedAuthority(SCOPE_PREFIX + scope));
        }
        for (String role : claimAsStrings(jwt, ROLES_CLAIM)) {
            authorities.add(new SimpleGrantedAuthority(ROLE_PREFIX + role));
        }
        return authorities;
    }

    /** Collects scopes from {@code scope} (space-delimited) and {@code scp}. */
    private static Collection<String> scopes(Jwt jwt) {
        Set<String> scopes = new LinkedHashSet<>();
        Object scopeClaim = jwt.getClaim(SCOPE_CLAIM);
        if (scopeClaim instanceof String spaceDelimited) {
            for (String token : spaceDelimited.split("\\s+")) {
                addIfNotBlank(scopes, token);
            }
        } else if (scopeClaim instanceof Collection<?> collection) {
            addEachAsString(scopes, collection);
        }
        scopes.addAll(claimAsStrings(jwt, SCP_CLAIM));
        return scopes;
    }

    /** Reads a claim that may be a single string or a collection of strings. */
    private static Collection<String> claimAsStrings(Jwt jwt, String claimName) {
        Object claim = jwt.getClaim(claimName);
        List<String> values = new ArrayList<>();
        if (claim instanceof String single) {
            // scp may also arrive space-delimited; split defensively.
            for (String token : single.split("\\s+")) {
                addIfNotBlank(values, token);
            }
        } else if (claim instanceof Collection<?> collection) {
            addEachAsString(values, collection);
        }
        return values;
    }

    private static void addEachAsString(Collection<String> target, Collection<?> source) {
        for (Object element : source) {
            if (element != null) {
                addIfNotBlank(target, element.toString());
            }
        }
    }

    private static void addIfNotBlank(Collection<String> target, String value) {
        if (value != null && !value.isBlank()) {
            target.add(value.trim());
        }
    }
}
