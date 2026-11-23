package hu.bme.szoftarch.identity.infrastructure.security;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Utility class to read and parse 2048-bit RSA PEM keys.
 * Handles PKCS#8 private keys (BEGIN PRIVATE KEY) and X.509 public keys (BEGIN PUBLIC KEY).
 */
public final class RsaKeyLoader {

    private RsaKeyLoader() {
    }

    public static RSAPrivateKey loadPrivateKey(Path path) throws Exception {
        String pem = Files.readString(path);
        return parsePrivateKey(pem);
    }

    public static RSAPublicKey loadPublicKey(Path path) throws Exception {
        String pem = Files.readString(path);
        return parsePublicKey(pem);
    }

    public static RSAPrivateKey parsePrivateKey(String pem) throws Exception {
        String clean = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s+", "");
        byte[] decoded = Base64.getDecoder().decode(clean);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(decoded);
        return (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(keySpec);
    }

    public static RSAPublicKey parsePublicKey(String pem) throws Exception {
        String clean = pem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s+", "");
        byte[] decoded = Base64.getDecoder().decode(clean);
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(decoded);
        return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(keySpec);
    }

    /**
     * Resolves key path by checking the given path, and fallbacks to parent directories if needed.
     */
    public static Path resolvePath(String pathStr) {
        Path direct = Path.of(pathStr);
        if (Files.exists(direct)) {
            return direct;
        }
        Path fromParent = Path.of("../../", pathStr);
        if (Files.exists(fromParent)) {
            return fromParent;
        }
        Path fromOneUp = Path.of("../", pathStr);
        if (Files.exists(fromOneUp)) {
            return fromOneUp;
        }
        return direct;
    }
}
