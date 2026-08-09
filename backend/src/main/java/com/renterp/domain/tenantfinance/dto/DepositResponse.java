package com.renterp.domain.tenantfinance.dto;

import com.renterp.domain.tenantfinance.entity.TenantDeposit;
import com.renterp.domain.tenantfinance.entity.TenantDeposit.DepositStatus;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
public class DepositResponse {

    private final UUID id;
    private final UUID membershipId;
    private final BigDecimal amount;
    private final String currency;
    private final DepositStatus status;
    private final String receivedAtBs;
    private final String notes;
    private final boolean active;
    private final Instant createdAt;
    private final Instant updatedAt;

    private DepositResponse(TenantDeposit d) {
        this.id = d.getId();
        this.membershipId = d.getMembershipId();
        this.amount = d.getAmount();
        this.currency = d.getCurrency();
        this.status = d.getStatus();
        this.receivedAtBs = d.getReceivedAtBs();
        this.notes = d.getNotes();
        this.active = d.isActive();
        this.createdAt = d.getCreatedAt();
        this.updatedAt = d.getUpdatedAt();
    }

    public static DepositResponse from(TenantDeposit d) { return new DepositResponse(d); }
}
