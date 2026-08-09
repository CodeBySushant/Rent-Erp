package com.renterp.domain.tenantfinance.dto;

import com.renterp.domain.tenantfinance.entity.RentIncrement;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
public class RentIncrementResponse {

    private final UUID id;
    private final UUID membershipId;
    private final UUID roomAssignmentId;
    private final BigDecimal previousAmount;
    private final BigDecimal newAmount;
    private final String effectiveBs;
    private final String reason;
    private final boolean notifiedTenant;
    private final UUID changedBy;
    private final Instant createdAt;
    private final Instant updatedAt;

    private RentIncrementResponse(RentIncrement r) {
        this.id = r.getId();
        this.membershipId = r.getMembershipId();
        this.roomAssignmentId = r.getRoomAssignmentId();
        this.previousAmount = r.getPreviousAmount();
        this.newAmount = r.getNewAmount();
        this.effectiveBs = r.getEffectiveBs();
        this.reason = r.getReason();
        this.notifiedTenant = r.isNotifiedTenant();
        this.changedBy = r.getChangedBy();
        this.createdAt = r.getCreatedAt();
        this.updatedAt = r.getUpdatedAt();
    }

    public static RentIncrementResponse from(RentIncrement r) { return new RentIncrementResponse(r); }
}
