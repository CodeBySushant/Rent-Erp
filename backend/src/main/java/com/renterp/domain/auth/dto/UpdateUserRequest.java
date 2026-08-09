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

    @Pattern(regexp = "^(en|ne)$", message = "Language must be 'en' or 'ne'")
    private String preferredLanguage;

    // FCM token updated when device re-registers for push notifications
    @Size(max = 512, message = "FCM token must not exceed 512 characters")
    private String fcmToken;
}
