package com.renterp.domain.tenancy.dto;

import com.renterp.domain.tenancy.entity.TenantPropertyMembership;
import com.renterp.domain.tenancy.entity.TenantPropertyMembership.MembershipStatus;
import com.renterp.domain.tenancy.entity.TenantPropertyMembership.PaymentModelOverride;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
public class MembershipResponse {

    private final UUID id;
    private final UUID tenantProfileId;
    private final UUID propertyId;
    private final MembershipStatus status;
    private final PaymentModelOverride paymentModelOverride;
    private final UUID linkedFromJoinRequestId;
    private final String startedAtBs;
    private final String endedAtBs;
    private final String terminationReason;
    private final boolean active;
    private final Instant createdAt;
    private final Instant updatedAt;

    private MembershipResponse(TenantPropertyMembership m) {
        this.id = m.getId();
        this.tenantProfileId = m.getTenantProfileId();
        this.propertyId = m.getPropertyId();
        this.status = m.getStatus();
        this.paymentModelOverride = m.getPaymentModelOverride();
        this.linkedFromJoinRequestId = m.getLinkedFromJoinRequestId();
        this.startedAtBs = m.getStartedAtBs();
        this.endedAtBs = m.getEndedAtBs();
        this.terminationReason = m.getTerminationReason();
        this.active = m.isActive();
        this.createdAt = m.getCreatedAt();
        this.updatedAt = m.getUpdatedAt();
    }

    public static MembershipResponse from(TenantPropertyMembership m) { return new MembershipResponse(m); }
}
