package com.renterp.domain.tenantfinance.dto;

import com.renterp.domain.tenantfinance.entity.TenantAdvanceRent;
import com.renterp.domain.tenantfinance.entity.TenantAdvanceRent.AdvanceStatus;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
public class AdvanceRentResponse {

    private final UUID id;
    private final UUID membershipId;
    private final BigDecimal amount;
    private final short monthsCovered;
    private final String coveredFromBs;
    private final String coveredToBs;
    private final AdvanceStatus status;
    private final String notes;
    private final boolean active;
    private final Instant createdAt;
    private final Instant updatedAt;

    private AdvanceRentResponse(TenantAdvanceRent a) {
        this.id = a.getId();
        this.membershipId = a.getMembershipId();
        this.amount = a.getAmount();
        this.monthsCovered = a.getMonthsCovered();
        this.coveredFromBs = a.getCoveredFromBs();
        this.coveredToBs = a.getCoveredToBs();
        this.status = a.getStatus();
        this.notes = a.getNotes();
        this.active = a.isActive();
        this.createdAt = a.getCreatedAt();
        this.updatedAt = a.getUpdatedAt();
    }

    public static AdvanceRentResponse from(TenantAdvanceRent a) { return new AdvanceRentResponse(a); }
}
