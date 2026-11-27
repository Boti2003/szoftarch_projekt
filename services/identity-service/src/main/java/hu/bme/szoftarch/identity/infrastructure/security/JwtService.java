package hu.bme.szoftarch.identity.infrastructure.security;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import hu.bme.szoftarch.identity.domain.User;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Core cryptographic service for:
 * 1. Generating RS256 signed Access Tokens (JWT)
 * 2. Parsing and verifying JWT signatures using the RSA public key
 * 3. Generating cryptographically secure random Refresh Tokens
 * 4. SHA-256 hashing for refresh tokens and user agent strings
 */
@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private final JwtProperties jwtProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    private RSAPrivateKey privateKey;
    private RSAPublicKey publicKey;

    @org.springframework.beans.factory.annotation.Autowired
    public JwtService(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    /**
     * Constructor for manual / unit test instantiation without Spring context.
     */
    public JwtService(JwtProperties jwtProperties, RSAPrivateKey privateKey, RSAPublicKey publicKey) {
        this.jwtProperties = jwtProperties;
        this.privateKey = privateKey;
        this.publicKey = publicKey;
    }

    @PostConstruct
    public void init() {
        if (privateKey == null && jwtProperties.getPrivateKeyPath() != null) {
            try {
                Path privPath = RsaKeyLoader.resolvePath(jwtProperties.getPrivateKeyPath());
                this.privateKey = RsaKeyLoader.loadPrivateKey(privPath);
                log.info("RSA private key loaded from {}", privPath);
            } catch (Exception e) {
                log.warn("RSA private key could not be loaded from {}: {}", jwtProperties.getPrivateKeyPath(), e.getMessage());
            }
        }
        if (publicKey == null && jwtProperties.getPublicKeyPath() != null) {
            try {
                Path pubPath = RsaKeyLoader.resolvePath(jwtProperties.getPublicKeyPath());
                this.publicKey = RsaKeyLoader.loadPublicKey(pubPath);
                log.info("RSA public key loaded from {}", pubPath);
            } catch (Exception e) {
                log.warn("RSA public key could not be loaded from {}: {}", jwtProperties.getPublicKeyPath(), e.getMessage());
            }
        }
    }

    /**
     * Issues a signed RS256 JWT Access Token.
     */
    public String generateAccessToken(User user) {
        if (privateKey == null) {
            throw new IllegalStateException("RSA Private key is not loaded, cannot issue tokens");
        }

        Instant now = Instant.now();
        Instant expiresAt = now.plus(jwtProperties.getAccessTokenTtlSeconds(), ChronoUnit.SECONDS);

        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .issuer(jwtProperties.getIssuer())
                .subject(user.getId().toString())
                .claim("username", user.getUsername())
                .claim("roles", List.of("ROLE_USER"))
                .jwtID(UUID.randomUUID().toString())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(expiresAt))
                .build();

        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .type(JOSEObjectType.JWT)
                .build();

        SignedJWT signedJWT = new SignedJWT(header, claimsSet);
        try {
            signedJWT.sign(new RSASSASigner(privateKey));
            return signedJWT.serialize();
        } catch (Exception e) {
            throw new RuntimeException("Failed to sign JWT access token", e);
        }
    }

    /**
     * Cryptographically verifies an RS256 token and returns its claims.
     */
    public JWTClaimsSet parseAndValidateToken(String token) {
        if (publicKey == null) {
            throw new IllegalStateException("RSA Public key is not loaded, cannot verify tokens");
        }

        try {
            SignedJWT signedJWT = SignedJWT.parse(token);
            boolean validSignature = signedJWT.verify(new RSASSAVerifier(publicKey));
            if (!validSignature) {
                log.warn("[SECURITY_ALERT: INVALID_SIGNATURE] Invalid JWT signature rejected");
                throw new IllegalArgumentException("Invalid JWT signature");
            }

            JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
            Date expiration = claims.getExpirationTime();
            if (expiration != null && expiration.before(new Date())) {
                log.debug("JWT token expired at {}", expiration);
                throw new IllegalArgumentException("JWT token has expired");
            }

            if (!jwtProperties.getIssuer().equals(claims.getIssuer())) {
                log.warn("[SECURITY_ALERT: UNKNOWN_ISSUER] Rejected JWT with untrusted issuer: {}", claims.getIssuer());
                throw new IllegalArgumentException("Invalid token issuer");
            }

            return claims;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[SECURITY_ALERT: MALFORMED_TOKEN] Malformed or unparseable JWT token: {}", e.getMessage());
            throw new IllegalArgumentException("Malformed or unparseable JWT token", e);
        }
    }

    /**
     * Generates a high-entropy, cryptographically secure 64-character hex refresh token.
     */
    public String generateRefreshToken() {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        return HexFormat.of().formatHex(randomBytes);
    }

    /**
     * Generates SHA-256 hash for secure storage of refresh tokens and user agent strings.
     */
    public String sha256(String input) {
        if (input == null) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm unavailable", e);
        }
    }

    public RSAPrivateKey getPrivateKey() {
        return privateKey;
    }

    public RSAPublicKey getPublicKey() {
        return publicKey;
    }
}
