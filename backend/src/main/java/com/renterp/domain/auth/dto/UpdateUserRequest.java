package com.renterp.domain.auth.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateUserRequest {

    @Size(max = 255, message = "Name must not exceed 255 characters")
    private String name;

    @Pattern(regexp = "^(en|hi|ne)$", message = "Language must be 'en', 'hi' or 'ne'")
    private String preferredLanguage;

    // Deprecated and ignored (M12): push tokens are registered per device with
    // POST /api/v1/me/devices. Kept so older app builds do not get a 400.
    @Size(max = 512, message = "FCM token must not exceed 512 characters")
    private String fcmToken;
}
