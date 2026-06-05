package com.github.mail;

import com.github.mail.utils.PasswordHasher;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class AccountPasswordHasherTest {

    @Test
    void hashAndVerify() {
        PasswordHasher hasher = new PasswordHasher();
        String encoded = hasher.hash("Passw0rd123");
        assertNotNull(encoded);
        assertTrue(hasher.verify("Passw0rd123", encoded));
        assertFalse(hasher.verify("Wrong", encoded));
    }
}
