package com.renterp.domain.auth.service;

/**
 * Password rules shared by registration and password change: 8-72 characters
 * (BCrypt ignores anything past 72 bytes), at least one letter and one digit.
 * The same rules are shown in the app's sign-up form.
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

    /** The problem with {@code password}, or null when it is acceptable. */
    public static String problem(String password) {
        if (password == null || password.length() < 8) {
            return "Use at least 8 characters";
        }
        if (password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            return "Use at most 72 characters";
        }
        boolean letter = false;
        boolean digit = false;
        for (int i = 0; i < password.length(); i++) {
            char c = password.charAt(i);
            if (Character.isLetter(c)) {
                letter = true;
            } else if (Character.isDigit(c)) {
                digit = true;
            }
        }
        if (!letter || !digit) {
            return "Include at least one letter and one number";
        }
        return null;
    }
}
