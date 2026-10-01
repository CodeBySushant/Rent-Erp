package com.renterp.domain.auth.dto;

import com.renterp.domain.auth.entity.User.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegisterRequest {

    // Letters (Latin or Devanagari), spaces, dot, apostrophe, hyphen; 2+ characters.
    @NotBlank(message = "Full name is required")
    @Size(min = 2, max = 255, message = "Name must be 2 to 255 characters")
    @Pattern(regexp = "^[A-Za-z\\u0900-\\u097F][A-Za-z\\u0900-\\u097F .'-]+$",
            message = "Name may contain letters, spaces, dots, apostrophes and hyphens only")
    private String name;

    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^(97|98)\\d{8}$", message = "Phone must be a valid 10-digit Nepal number starting with 97 or 98")
    private String phone;

    @NotBlank(message = "Email is required")
    @Email(message = "Enter a valid email address")
    @Size(max = 254, message = "Email must not exceed 254 characters")
    private String email;

    // Strength rules are checked in PasswordPolicy so the message is specific.
    @NotBlank(message = "Password is required")
    private String password;

    @NotNull(message = "role is required (LANDLORD or TENANT)")
    private UserRole role;

    @Pattern(regexp = "^(en|hi|ne)$", message = "Language must be 'en', 'hi' or 'ne'")
    private String preferredLanguage = "en";

    @NotBlank(message = "verificationToken is required - verify the phone number first")
    private String verificationToken;
}
