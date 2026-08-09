package com.renterp.domain.tenancy.dto;

import com.renterp.domain.tenancy.entity.TenantPropertyMembership.PaymentModelOverride;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateMembershipRequest {
    // Only paymentModelOverride is edit-friendly. status transitions are their own endpoint.
    private PaymentModelOverride paymentModelOverride;
}
