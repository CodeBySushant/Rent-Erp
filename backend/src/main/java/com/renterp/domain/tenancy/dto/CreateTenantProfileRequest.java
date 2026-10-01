package com.renterp.domain.tenancy.dto;

import com.renterp.domain.tenancy.entity.TenantProfile.TenantCategory;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class CreateTenantProfileRequest {

    // NULL → unlinked tenant (landlord entered). Non-NULL → linked to a users row.
    private UUID userId;

    @NotBlank(message = "fullName is required")
    @Size(max = 255)
    private String fullName;

    @NotBlank(message = "phone is required")
    @Pattern(regexp = "^(97|98)\\d{8}$", message = "phone must be a valid 10-digit Nepal number starting with 97 or 98")
    private String phone;

    @Size(max = 20)
    private String whatsappNumber;

    @Pattern(regexp = "^(en|hi|ne)$", message = "preferredLanguage must be 'en', 'hi' or 'ne'")
    private String preferredLanguage = "en";

    private TenantCategory tenantCategory = TenantCategory.INDIVIDUAL;

    @Min(1) @Max(50)
    private short numOccupants = 1;

    @Size(max = 255)
    private String emergencyContactName;

    @Size(max = 20)
    private String emergencyContactPhone;
}
