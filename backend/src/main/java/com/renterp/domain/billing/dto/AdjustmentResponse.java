package com.renterp.domain.billing.dto;

import com.renterp.domain.billing.entity.TenantBillAdjustment;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
public class AdjustmentResponse {

    private final UUID id;
    private final UUID membershipId;
    private final UUID propertyId;
    private final String adjustmentType;
    private final String source;
    private final BigDecimal amount;
    private final String reason;
    private final UUID originBillId;
    private final UUID appliedBillId;
    private final String status;
    private final UUID createdBy;
    private final Instant createdAt;
    private final Instant updatedAt;

    private AdjustmentResponse(TenantBillAdjustment a) {
        this.id = a.getId();
        this.membershipId = a.getMembershipId();
        this.propertyId = a.getPropertyId();
        this.adjustmentType = a.getAdjustmentType().name();
        this.source = a.getSource().name();
        this.amount = a.getAmount();
        this.reason = a.getReason();
        this.originBillId = a.getOriginBillId();
        this.appliedBillId = a.getAppliedBillId();
        this.status = a.getStatus().name();
        this.createdBy = a.getCreatedBy();
        this.createdAt = a.getCreatedAt();
        this.updatedAt = a.getUpdatedAt();
    }

    public static AdjustmentResponse from(TenantBillAdjustment a) { return new AdjustmentResponse(a); }
}
