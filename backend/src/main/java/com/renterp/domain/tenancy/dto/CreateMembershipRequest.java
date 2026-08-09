package com.renterp.domain.tenancy.dto;

import com.renterp.domain.tenancy.entity.TenantPropertyMembership.PaymentModelOverride;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class CreateMembershipRequest {

    @NotNull(message = "tenantProfileId is required")
    private UUID tenantProfileId;

    @NotNull(message = "propertyId is required")
    private UUID propertyId;

    // BS move-in date. Required both here (unlinked/direct path) and inside the
    // join-request-accept flow (which builds this same object internally).
    @NotBlank(message = "startedAtBs is required")
    @Pattern(regexp = "^[0-9]{4}-[0-9]{2}-[0-9]{2}$",
             message = "startedAtBs must be YYYY-MM-DD BS format")
    @Size(max = 20)
    private String startedAtBs;

    // NULL = fall back to Property.paymentModelDefault (T10).
    private PaymentModelOverride paymentModelOverride;
}
