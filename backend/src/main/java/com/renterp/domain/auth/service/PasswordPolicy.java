package com.renterp.domain.auth.service;

/**
 * Password rules shared by registration, password reset and password change.
 * They are exactly the app's checklist (password_requirements.dart), so the
 * server never accepts a password the app would refuse, whichever app
 * version or client sends it:
 * <ul>
 *   <li>8 characters or more, and at most 72 bytes (BCrypt ignores the rest);</li>
 *   <li>an uppercase letter A–Z and a lowercase letter a–z;</li>
 *   <li>a number 0–9;</li>
 *   <li>a special character: anything that is not A–Z, a–z, 0–9 or a space.</li>
 * </ul>
 * Only new passwords are checked; existing ones keep working at login.
 */
public final class PasswordPolicy {

    private PasswordPolicy() {
    }

    /**
     * True when {@code password} is longer than BCrypt can handle (72 bytes).
     * Such a value is never a stored password, so callers reject it before
     * calling the encoder, which may otherwise throw and answer 500 (L2).
     */
    public static boolean tooLongForBcrypt(String password) {
        return password != null && password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72;
    }

    /** The first problem with {@code password}, in the app's checklist order, or null. */
    public static String problem(String password) {
        if (password == null || password.length() < 8) {
            return "Use at least 8 characters";
        }
        if (tooLongForBcrypt(password)) {
            return "Use at most 72 characters";
        }
        boolean upper = false;
        boolean lower = false;
        boolean digit = false;
        boolean special = false;
        for (int i = 0; i < password.length(); i++) {
            char c = password.charAt(i);
            if (c >= 'A' && c <= 'Z') {
                upper = true;
            } else if (c >= 'a' && c <= 'z') {
                lower = true;
            } else if (c >= '0' && c <= '9') {
                digit = true;
            } else if (!Character.isWhitespace(c)) {
                special = true;
            }
        }
        if (!upper) {
            return "Include an uppercase letter (A-Z)";
        }
        if (!lower) {
            return "Include a lowercase letter (a-z)";
        }
        if (!digit) {
            return "Include a number (0-9)";
        }
        if (!special) {
            return "Include a special character (e.g. ! @ # $)";
        }
        return null;
    }
}
