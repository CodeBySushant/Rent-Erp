package com.renterp.domain.tenantfinance.dto;

import com.renterp.domain.tenantfinance.entity.TenantOpeningBalance;
import com.renterp.domain.tenantfinance.entity.TenantOpeningBalance.Direction;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
public class OpeningBalanceResponse {

    private final UUID id;
    private final UUID membershipId;
    private final BigDecimal amount;
    private final Direction direction;
    private final String asOfBs;
    private final String notes;
    private final Instant createdAt;
    private final Instant updatedAt;

    private OpeningBalanceResponse(TenantOpeningBalance b) {
        this.id = b.getId();
        this.membershipId = b.getMembershipId();
        this.amount = b.getAmount();
        this.direction = b.getDirection();
        this.asOfBs = b.getAsOfBs();
        this.notes = b.getNotes();
        this.createdAt = b.getCreatedAt();
        this.updatedAt = b.getUpdatedAt();
    }

    public static OpeningBalanceResponse from(TenantOpeningBalance b) { return new OpeningBalanceResponse(b); }
}
