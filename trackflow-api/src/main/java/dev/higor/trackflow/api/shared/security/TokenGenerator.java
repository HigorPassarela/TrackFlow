package dev.higor.trackflow.api.shared.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

/**
 * Secrets for invite and share links. The plain token goes to the caller once; only its SHA-256
 * hash is stored, so a database leak does not reveal usable links.
 */
@Component
public class TokenGenerator {

    private static final int TOKEN_BYTES = 32;
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final HexFormat HEX = HexFormat.of();

    private final SecureRandom random = new SecureRandom();

    public GeneratedToken generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        String value = ENCODER.encodeToString(bytes);
        return new GeneratedToken(value, hash(value));
    }

    /** Lowercase hex SHA-256 of the token, the form stored in {@code token_hash} columns. */
    public String hash(String token) {
        return HEX.formatHex(sha256().digest(token.getBytes(StandardCharsets.UTF_8)));
    }

    /** Constant-time comparison, so response timing does not leak how much of a hash matched. */
    public boolean matches(String token, String expectedHash) {
        return MessageDigest.isEqual(
                hash(token).getBytes(StandardCharsets.US_ASCII), expectedHash.getBytes(StandardCharsets.US_ASCII));
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by every Java platform", e);
        }
    }

    /** {@code value} is shown to the user once; {@code hash} is what gets persisted. */
    public record GeneratedToken(String value, String hash) {}
}
