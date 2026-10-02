package com.renterp.domain.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DeviceTokenRequest {

    @NotBlank(message = "token is required")
    @Size(max = 500)
    private String token;

    @NotBlank(message = "platform is required")
    @Pattern(regexp = "ANDROID|IOS|WEB", message = "platform must be ANDROID, IOS or WEB")
    private String platform;
}
