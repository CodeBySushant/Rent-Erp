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

    // M15: Nepal mobile numbers only, like every phone field in the API.
    @Pattern(regexp = com.renterp.common.validation.NepalPhone.REGEX, message = "whatsappNumber: " + com.renterp.common.validation.NepalPhone.MESSAGE)
    private String whatsappNumber;

    @Pattern(regexp = "^(en|hi|ne)$", message = "preferredLanguage must be 'en', 'hi' or 'ne'")
    private String preferredLanguage;

    private TenantCategory tenantCategory;

    @Min(1) @Max(50)
    private Short numOccupants;

    @Size(max = 255)
    private String emergencyContactName;

    // M15: Nepal mobile numbers only, like every phone field in the API.
    @Pattern(regexp = com.renterp.common.validation.NepalPhone.REGEX, message = "emergencyContactPhone: " + com.renterp.common.validation.NepalPhone.MESSAGE)
    private String emergencyContactPhone;
}
