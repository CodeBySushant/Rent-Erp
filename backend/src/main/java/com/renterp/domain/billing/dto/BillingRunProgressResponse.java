package com.renterp.domain.billing.dto;

import com.renterp.domain.billing.entity.BillingRunProgress;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
public class BillingRunProgressResponse {

    private final UUID id;
    private final UUID billingRunId;
    private final int totalTenants;
    private final int processedTenants;
    private final int failedTenants;
    private final String status;
    private final String lastError;
    private final Instant startedAt;
    private final Instant updatedAt;

    private BillingRunProgressResponse(BillingRunProgress p) {
        this.id = p.getId();
        this.billingRunId = p.getBillingRunId();
        this.totalTenants = p.getTotalTenants();
        this.processedTenants = p.getProcessedTenants();
        this.failedTenants = p.getFailedTenants();
        this.status = p.getStatus().name();
        this.lastError = p.getLastError();
        this.startedAt = p.getStartedAt();
        this.updatedAt = p.getUpdatedAt();
    }

    public static BillingRunProgressResponse from(BillingRunProgress p) {
        return new BillingRunProgressResponse(p);
    }
}
