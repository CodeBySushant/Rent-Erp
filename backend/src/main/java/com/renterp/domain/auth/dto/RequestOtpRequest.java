package com.renterp.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RequestOtpRequest {

    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^(97|98)\\d{8}$", message = "Phone must be a valid 10-digit Nepal number starting with 97 or 98")
    private String phone;

    /** SIGNUP or LOGIN. (CHANGE_PHONE codes are requested by a logged-in user elsewhere.) */
    @NotNull(message = "purpose is required (SIGNUP or LOGIN)")
    private Purpose purpose;

    public enum Purpose {
        SIGNUP, LOGIN
    }
}
