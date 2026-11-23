package hu.bme.szoftarch.identity.infrastructure.security;

import com.nimbusds.jwt.JWTClaimsSet;
import hu.bme.szoftarch.identity.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pure unit tests for JwtService, RsaKeyLoader, and password encoding.
 * Runs in memory without Spring context.
 */
@ExtendWith(OutputCaptureExtension.class)
class JwtServiceTest {

    private JwtService jwtService;
    private JwtProperties jwtProperties;
    private RSAPrivateKey privateKey;
    private RSAPublicKey publicKey;

    @BeforeEach
    void setUp() throws Exception {
        // Generate dynamic 2048-bit RSA key pair for testing
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair keyPair = kpg.generateKeyPair();
        this.privateKey = (RSAPrivateKey) keyPair.getPrivate();
        this.publicKey = (RSAPublicKey) keyPair.getPublic();

        this.jwtProperties = new JwtProperties();
        this.jwtProperties.setIssuer("szoftarch-test");
        this.jwtProperties.setAccessTokenTtlSeconds(300);

        this.jwtService = new JwtService(jwtProperties, privateKey, publicKey);
    }

    @Test
    @DisplayName("Generate access token and verify signature, claims, and issuer")
    void testGenerateAndValidateAccessToken() throws Exception {
        User user = new User("alice@test.com", "alice", "Alice Test", "pwd_hash");
        user.setId(UUID.randomUUID());

        String token = jwtService.generateAccessToken(user);
        assertThat(token).isNotBlank();

        JWTClaimsSet claims = jwtService.parseAndValidateToken(token);
        assertThat(claims.getIssuer()).isEqualTo("szoftarch-test");
        assertThat(claims.getSubject()).isEqualTo(user.getId().toString());
        assertThat(claims.getStringClaim("username")).isEqualTo("alice");
        assertThat(claims.getStringListClaim("roles")).contains("ROLE_USER");
        assertThat(claims.getExpirationTime()).isAfter(claims.getIssueTime());
    }

    @Test
    @DisplayName("Tampered token signature must be rejected and logged")
    void testTamperedTokenIsRejected(CapturedOutput output) {
        User user = new User("bob@test.com", "bob", "Bob Test", "pwd_hash");
        String token = jwtService.generateAccessToken(user);

        // Tamper with a character in the signature (avoiding base64 padding bits at the end)
        int sigIdx = token.lastIndexOf('.') + 5;
        char c = token.charAt(sigIdx);
        char alteredChar = (c == 'a') ? 'b' : 'a';
        String tamperedToken = token.substring(0, sigIdx) + alteredChar + token.substring(sigIdx + 1);

        assertThatThrownBy(() -> jwtService.parseAndValidateToken(tamperedToken))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid JWT signature");

        assertThat(output.getOut()).contains("[SECURITY_ALERT: INVALID_SIGNATURE]");
    }

    @Test
    @DisplayName("Untrusted issuer must be rejected and logged as security alert")
    void testUntrustedIssuerIsRejectedAndLogged(CapturedOutput output) {
        JwtProperties attackerProps = new JwtProperties();
        attackerProps.setIssuer("attacker-issuer");
        attackerProps.setAccessTokenTtlSeconds(300);
        JwtService attackerJwtService = new JwtService(attackerProps, privateKey, publicKey);

        User user = new User("dan@test.com", "dan", "Dan Test", "pwd_hash");
        user.setId(UUID.randomUUID());
        String tokenWithUntrustedIssuer = attackerJwtService.generateAccessToken(user);

        assertThatThrownBy(() -> jwtService.parseAndValidateToken(tokenWithUntrustedIssuer))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid token issuer");

        assertThat(output.getOut()).contains("[SECURITY_ALERT: UNKNOWN_ISSUER]");
    }

    @Test
    @DisplayName("Expired token must be rejected")
    void testExpiredTokenIsRejected() {
        jwtProperties.setAccessTokenTtlSeconds(-10); // Expired in the past
        User user = new User("carol@test.com", "carol", "Carol Test", "pwd_hash");

        String expiredToken = jwtService.generateAccessToken(user);

        assertThatThrownBy(() -> jwtService.parseAndValidateToken(expiredToken))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expired");
    }

    @Test
    @DisplayName("Generate 64-character hex refresh token and deterministic SHA-256 hash")
    void testRefreshTokenGenerationAndHashing() {
        String rt1 = jwtService.generateRefreshToken();
        String rt2 = jwtService.generateRefreshToken();

        assertThat(rt1).hasSize(64);
        assertThat(rt2).hasSize(64);
        assertThat(rt1).isNotEqualTo(rt2);

        String hash1 = jwtService.sha256(rt1);
        String hash1Repeat = jwtService.sha256(rt1);

        assertThat(hash1).hasSize(64);
        assertThat(hash1).isEqualTo(hash1Repeat);
        assertThat(hash1).isNotEqualTo(rt1);

        // User-Agent hashing
        String uaHash = jwtService.sha256("Mozilla/5.0 (X11; Linux x86_64) Chrome/131.0");
        assertThat(uaHash).hasSize(64);
    }

    @Test
    @DisplayName("Parse PKCS8 and X509 PEM strings with RsaKeyLoader")
    void testRsaKeyLoaderPemParsing() throws Exception {
        String privatePem = "-----BEGIN PRIVATE KEY-----\n" +
                Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(privateKey.getEncoded()) +
                "\n-----END PRIVATE KEY-----";

        String publicPem = "-----BEGIN PUBLIC KEY-----\n" +
                Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(publicKey.getEncoded()) +
                "\n-----END PUBLIC KEY-----";

        RSAPrivateKey parsedPrivate = RsaKeyLoader.parsePrivateKey(privatePem);
        RSAPublicKey parsedPublic = RsaKeyLoader.parsePublicKey(publicPem);

        assertThat(parsedPrivate.getModulus()).isEqualTo(privateKey.getModulus());
        assertThat(parsedPublic.getModulus()).isEqualTo(publicKey.getModulus());
    }

    @Test
    @DisplayName("BCrypt password encoder matches encoded password")
    void testPasswordEncoder() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String rawPassword = "securePassword123";

        String encoded = encoder.encode(rawPassword);
        assertThat(encoded).isNotEqualTo(rawPassword);
        assertThat(encoder.matches(rawPassword, encoded)).isTrue();
        assertThat(encoder.matches("wrongPassword", encoded)).isFalse();
    }
}
