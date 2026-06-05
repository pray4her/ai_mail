package com.github.mail.utils;

import org.springframework.stereotype.Component;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class PasswordHasher {

    private static final String ALGORITHM = "pbkdf2_sha256";
    private static final String KDF = "PBKDF2WithHmacSHA256";
    private static final int DEFAULT_ITERATIONS = 120_000;
    private static final int SALT_BYTES = 16;
    private static final int KEY_BYTES = 32;

    private final SecureRandom secureRandom = new SecureRandom();
    private final Base64.Encoder b64Encoder = Base64.getUrlEncoder().withoutPadding();
    private final Base64.Decoder b64Decoder = Base64.getUrlDecoder();

    public String hash(final String rawPassword) {
        if (rawPassword == null) {
            throw new IllegalArgumentException("rawPassword is null");
        }
        byte[] salt = new byte[SALT_BYTES];
        secureRandom.nextBytes(salt);
        byte[] derived = derive(rawPassword.toCharArray(), salt, DEFAULT_ITERATIONS, KEY_BYTES);
        return ALGORITHM + "$" + DEFAULT_ITERATIONS + "$" + b64Encoder.encodeToString(salt) + "$" + b64Encoder.encodeToString(derived);
    }

    public boolean verify(final String rawPassword, final String encoded) {
        if (rawPassword == null || encoded == null || encoded.isBlank()) {
            return false;
        }
        String[] parts = encoded.split("\\$");
        if (parts.length != 4) {
            return false;
        }
        if (!ALGORITHM.equals(parts[0])) {
            return false;
        }
        int iterations;
        try {
            iterations = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            return false;
        }
        byte[] salt;
        byte[] expected;
        try {
            salt = b64Decoder.decode(parts[2]);
            expected = b64Decoder.decode(parts[3]);
        } catch (IllegalArgumentException e) {
            return false;
        }
        byte[] actual = derive(rawPassword.toCharArray(), salt, iterations, expected.length);
        return constantTimeEquals(actual, expected);
    }

    private byte[] derive(final char[] password, final byte[] salt, final int iterations, final int keyBytes) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, keyBytes * 8);
            return SecretKeyFactory.getInstance(KDF).generateSecret(spec).getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private boolean constantTimeEquals(final byte[] a, final byte[] b) {
        if (a == null || b == null || a.length != b.length) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length; i++) {
            result |= a[i] ^ b[i];
        }
        return result == 0;
    }
}
