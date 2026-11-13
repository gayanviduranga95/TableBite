package com.tablebite.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityUtilTest {

    @Test
    void hashesAreSaltedAndVerifiable() {
        String first = SecurityUtil.hashPassword("Secret123");
        String second = SecurityUtil.hashPassword("Secret123");
        assertNotEquals(first, second, "each hash uses a fresh salt");
        assertTrue(first.startsWith("pbkdf2_sha256$"));
        assertTrue(SecurityUtil.verifyPassword("Secret123", first));
        assertFalse(SecurityUtil.verifyPassword("secret123", first));
        assertFalse(SecurityUtil.verifyPassword("Secret123", "plain-text"));
        assertFalse(SecurityUtil.verifyPassword(null, first));
    }

    @Test
    void tokensAreRandomAndHashStable() {
        assertNotEquals(SecurityUtil.randomToken(32), SecurityUtil.randomToken(32));
        assertEquals(32, SecurityUtil.randomHex(16).length());
        assertEquals(SecurityUtil.sha256Hex("abc"), SecurityUtil.sha256Hex("abc"));
        assertEquals(64, SecurityUtil.sha256Hex("abc").length());
        assertTrue(SecurityUtil.constantTimeEquals("a", "a"));
        assertFalse(SecurityUtil.constantTimeEquals("a", null));
    }
}
