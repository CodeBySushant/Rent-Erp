package com.renterp.domain.auth.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PasswordPolicyTest {

    @Test
    void acceptsLetterAndDigitEightOrMore() {
        assertNull(PasswordPolicy.problem("rentlo123"));
        assertNull(PasswordPolicy.problem("abcdefg1"));
    }

    @Test
    void rejectsShortMissingDigitMissingLetterAndTooLong() {
        assertEquals("Use at least 8 characters", PasswordPolicy.problem("abc123"));
        assertEquals("Use at least 8 characters", PasswordPolicy.problem(null));
        assertEquals("Include at least one letter and one number", PasswordPolicy.problem("abcdefgh"));
        assertEquals("Include at least one letter and one number", PasswordPolicy.problem("12345678"));
        assertEquals("Use at most 72 characters", PasswordPolicy.problem("a1".repeat(40)));
    }
}
