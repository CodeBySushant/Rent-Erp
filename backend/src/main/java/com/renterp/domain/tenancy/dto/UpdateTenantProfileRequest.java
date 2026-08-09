package com.renterp.domain.tenancy.dto;

import com.renterp.domain.tenancy.entity.TenantProfile.TenantCategory;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateTenantProfileRequest {

    // userId, phone, and the linked/unlinked shape are NOT updatable — they're
    // identity. Rebinding to a different user is a whole different operation.

    @Size(max = 255)
    private String fullName;

    @Size(max = 20)
    private String whatsappNumber;

    @Pattern(regexp = "^(en|ne)$", message = "preferredLanguage must be 'en' or 'ne'")
    private String preferredLanguage;

    private TenantCategory tenantCategory;

    @Min(1) @Max(50)
    private Short numOccupants;

    @Size(max = 255)
    private String emergencyContactName;

    @Size(max = 20)
    private String emergencyContactPhone;
}
