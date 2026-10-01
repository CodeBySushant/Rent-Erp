package com.renterp.domain.auth.security;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenHasherTest {

    @Test
    void codesAreSixDigits() {
        for (int i = 0; i < 200; i++) {
            String code = TokenHasher.randomDigits(6);
            assertTrue(code.matches("\\d{6}"), code);
        }
    }

    @Test
    void tokensAreRandomAndUrlSafe() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            String t = TokenHasher.randomToken(32);
            assertTrue(t.matches("[A-Za-z0-9_-]{43}"), t);
            assertTrue(seen.add(t));
        }
    }

    @Test
    void sha256IsStableHex() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                TokenHasher.sha256Hex("abc"));
        assertNotEquals(TokenHasher.sha256Hex("a"), TokenHasher.sha256Hex("b"));
    }
}
