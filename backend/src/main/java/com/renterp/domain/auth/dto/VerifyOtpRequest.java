package com.renterp.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/** Body of /auth/otp/verify (sign-up) and /auth/login (OTP login). */
@Getter
@Setter
public class VerifyOtpRequest {

    @NotNull(message = "verificationId is required")
    private UUID verificationId;

    @NotBlank(message = "Code is required")
    @Pattern(regexp = "^\\d{6}$", message = "Code must be 6 digits")
    private String code;
}
