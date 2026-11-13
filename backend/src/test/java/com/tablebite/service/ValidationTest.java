package com.tablebite.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ValidationTest {

    @Test
    void validatesEmailsAndPasswords() {
        assertEquals("guest@example.com", Validation.email(" Guest@Example.com "));
        assertThrows(IllegalArgumentException.class, () -> Validation.email("not-an-email"));
        assertThrows(IllegalArgumentException.class, () -> Validation.password("short1"));
        assertThrows(IllegalArgumentException.class, () -> Validation.password("onlyletters"));
        assertEquals("Secret123", Validation.password("Secret123"));
    }

    @Test
    void validatesPricesAndImages() {
        assertEquals(new BigDecimal("12.50"), Validation.price(new BigDecimal("12.5")));
        assertThrows(IllegalArgumentException.class, () -> Validation.price(new BigDecimal("-1")));
        assertThrows(IllegalArgumentException.class, () -> Validation.price(new BigDecimal("1.234")));
        assertEquals("/uploads/ab.jpg", Validation.imageUrl("/uploads/ab.jpg"));
        assertNull(Validation.imageUrl(" "));
        assertThrows(IllegalArgumentException.class, () -> Validation.imageUrl("javascript:alert(1)"));
        assertThrows(IllegalArgumentException.class, () -> Validation.imageUrl("/images/../../etc/passwd"));
        assertThrows(IllegalArgumentException.class, () -> Validation.imageUrl("http://insecure.example/x.jpg"));
    }

    @Test
    void idempotencyKeysAreRestricted() {
        assertNull(Validation.idempotencyKey(null));
        assertEquals("abcd-1234_XY", Validation.idempotencyKey("abcd-1234_XY"));
        assertThrows(IllegalArgumentException.class, () -> Validation.idempotencyKey("bad key!"));
    }
}
