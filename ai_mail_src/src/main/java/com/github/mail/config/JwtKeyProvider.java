package com.github.mail.config;

import com.github.mail.model.config.Properties.AccountAuthProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Component
@RequiredArgsConstructor
public class JwtKeyProvider {

    private final AccountAuthProperties accountAuthProperties;

    public byte[] getSecretKeyBytes() {
        String secret = accountAuthProperties.getJwtSecret();
        if (secret == null || secret.isBlank()) {
            secret = SystemResource.SECRET_KEY;
        }
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length >= 32) {
            return keyBytes;
        }
        try {
            return MessageDigest.getInstance("SHA-256").digest(keyBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
