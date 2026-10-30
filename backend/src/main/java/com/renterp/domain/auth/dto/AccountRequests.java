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

    /** PUT /me/photo: a PROFILE_PHOTO upload of the caller. */
    @Getter
    @Setter
    public static class SetPhoto {
        @NotNull(message = "fileId is required")
        private UUID fileId;
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
