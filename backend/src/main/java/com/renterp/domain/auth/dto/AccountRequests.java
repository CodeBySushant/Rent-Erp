package com.renterp.domain.auth.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/** Request bodies for account self-service (/me/...). */
public final class AccountRequests {

    private AccountRequests() {
    }

    @Getter
    @Setter
    public static class ChangePassword {
        /** Required when the account already has a password. */
        private String currentPassword;

        @NotBlank(message = "newPassword is required")
        private String newPassword;
    }

    @Getter
    @Setter
    public static class NewPhone {
        @NotBlank(message = "phone is required")
        @Pattern(regexp = "^(97|98)\\d{8}$", message = "Phone must be a valid 10-digit Nepal number starting with 97 or 98")
        private String phone;
    }

    @Getter
    @Setter
    public static class NewEmail {
        @NotBlank(message = "email is required")
        @Email(message = "Enter a valid email address")
        @Size(max = 254)
        private String email;
    }

    @Getter
    @Setter
    public static class ConfirmCode {
        @NotNull(message = "verificationId is required")
        private UUID verificationId;

        @NotBlank(message = "code is required")
        private String code;
    }

    @Getter
    @Setter
    public static class DeleteAccount {
        /** Must be exactly DELETE. */
        @NotBlank(message = "confirm is required")
        private String confirm;

        /** Required when the account has a password. */
        private String password;
    }
}
