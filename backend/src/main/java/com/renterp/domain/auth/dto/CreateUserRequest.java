package com.renterp.domain.auth.dto;

import com.renterp.domain.auth.entity.User.UserRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateUserRequest {

    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^(97|98)\\d{8}$", message = "Phone must be a valid 10-digit Nepal number starting with 97 or 98")
    private String phone;

    @Size(max = 255, message = "Name must not exceed 255 characters")
    private String name;

    private UserRole role = UserRole.LANDLORD;

    @Pattern(regexp = "^(en|hi|ne)$", message = "Language must be 'en', 'hi' or 'ne'")
    private String preferredLanguage = "en";
}
