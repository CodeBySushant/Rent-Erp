package com.renterp.domain.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/** Bodies of the public forgotten-password endpoints under /api/v1/auth/password. */
public final class PasswordResetRequests {

    private PasswordResetRequests() {
    }

    /**
     * POST /auth/password/forgot - exactly one of {@code email} or {@code phone}:
     * where the account's reset code is sent.
     */
    @Getter
    @Setter
    public static class Forgot {
        @Email(message = "Enter a valid email address")
        @Size(max = 254)
        private String email;

        @Pattern(regexp = "^$|^(97|98)\\d{8}$", message = "Phone must be a valid 10-digit Nepal number starting with 97 or 98")
        private String phone;
    }

    /** POST /auth/password/reset - the code from /forgot and the new password. */
    @Getter
    @Setter
    public static class Reset {
        @NotNull(message = "verificationId is required")
        private UUID verificationId;

        @NotBlank(message = "Code is required")
        @Pattern(regexp = "^\\d{6}$", message = "Code must be 6 digits")
        private String code;

        @NotBlank(message = "newPassword is required")
        private String newPassword;
    }
}
