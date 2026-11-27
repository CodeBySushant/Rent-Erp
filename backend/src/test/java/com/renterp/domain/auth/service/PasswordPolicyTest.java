package com.renterp.domain.auth.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Server rules = the app's checklist: 8+, upper, lower, number, special. */
class PasswordPolicyTest {

    @Test
    void acceptsAPasswordMeetingEveryRule() {
        assertNull(PasswordPolicy.problem("Rentlo@123"));
        assertNull(PasswordPolicy.problem("Abcdef1!"));
        assertNull(PasswordPolicy.problem("My Pass 9#"));   // spaces allowed
        assertNull(PasswordPolicy.problem("Ab1_cdef"));     // underscore is special
    }

    @Test
    void refusesEachMissingRuleInChecklistOrder() {
        assertEquals("Use at least 8 characters", PasswordPolicy.problem("Ab1!"));
        assertEquals("Use at least 8 characters", PasswordPolicy.problem(null));
        assertEquals("Include an uppercase letter (A-Z)", PasswordPolicy.problem("rentlo@123"));
        assertEquals("Include a lowercase letter (a-z)", PasswordPolicy.problem("RENTLO@123"));
        assertEquals("Include a number (0-9)", PasswordPolicy.problem("Rentlo@abc"));
        assertEquals("Include a special character (e.g. ! @ # $)", PasswordPolicy.problem("Rentlo1234"));
        // A space is not a special character (same as the app).
        assertEquals("Include a special character (e.g. ! @ # $)", PasswordPolicy.problem("Rentlo 1234"));
    }

    @Test
    void oldStyleWeakPasswordsAreNowRefused() {
        assertEquals("Include an uppercase letter (A-Z)", PasswordPolicy.problem("rentlo123"));
        assertEquals("Include an uppercase letter (A-Z)", PasswordPolicy.problem("abcdefg1"));
    }

    @Test
    void tooLongIsRefused() {
        assertEquals("Use at most 72 characters", PasswordPolicy.problem("Aa1!".repeat(19)));
        assertNull(PasswordPolicy.problem("Aa1!".repeat(18)));
    }

    @Test
    void longerThanBcryptIsFlagged() {   // L2
        org.junit.jupiter.api.Assertions.assertTrue(PasswordPolicy.tooLongForBcrypt("a1".repeat(37)));
        org.junit.jupiter.api.Assertions.assertFalse(PasswordPolicy.tooLongForBcrypt("a1".repeat(36)));
        org.junit.jupiter.api.Assertions.assertFalse(PasswordPolicy.tooLongForBcrypt(null));
    }
}
