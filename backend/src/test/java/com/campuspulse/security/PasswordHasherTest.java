package com.campuspulse.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PasswordHasherTest {
    private final PasswordHasher hasher = new PasswordHasher();

    @Test
    void hashesAreSaltedAndOnlyMatchTheOriginalPassword() {
        String first = hasher.hash("correct horse battery staple");
        String second = hasher.hash("correct horse battery staple");

        assertNotEquals(first, second);
        assertTrue(hasher.matches("correct horse battery staple", first));
        assertFalse(hasher.matches("wrong password", first));
        assertFalse(hasher.matches("correct horse battery staple", "invalid"));
    }
}
