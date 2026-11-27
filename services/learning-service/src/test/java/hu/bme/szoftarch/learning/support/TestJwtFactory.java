package hu.bme.szoftarch.learning.support;

import java.io.InputStream;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import org.springframework.core.io.ClassPathResource;

/**
 * RS256 teszt-token gyár a resource server validáció teszteléséhez. A privát
 * kulcs CSAK teszt célú; az éles privát kulcs az Identity service-é.
 */
public final class TestJwtFactory {

    private static final String ISSUER = "szoftarch-nyelvtanulo";

    private TestJwtFactory() {
    }

    public static String validToken(String subject) {
        try {
            RSAPrivateKey privateKey = loadPrivateKey();
            Instant now = Instant.now();
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(subject)
                    .issuer(ISSUER)
                    .issueTime(java.util.Date.from(now))
                    .expirationTime(java.util.Date.from(now.plusSeconds(900)))
                    .build();
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
            jwt.sign(new RSASSASigner(privateKey));
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to mint test JWT", e);
        }
    }

    private static RSAPrivateKey loadPrivateKey() throws Exception {
        try (InputStream in = new ClassPathResource("keys/jwt-private-test.key8").getInputStream()) {
            String pem = new String(in.readAllBytes())
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] der = Base64.getDecoder().decode(pem);
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(der);
            return (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(spec);
        }
    }
}
