package com.renterp.domain.billing.dto;

import com.renterp.domain.billing.entity.BillingRun;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
public class BillingRunResponse {

    private final UUID id;
    private final UUID propertyId;
    private final String billingMonthBs;
    private final String periodStartBs;
    private final String periodEndBs;
    private final String status;
    private final String tariffMode;
    private final UUID tariffVersionId;
    private final BigDecimal neaBlendedRate;
    private final BigDecimal neaTotalBill;
    private final int tenantCount;
    private final BigDecimal totalBilled;
    private final String generatedAtBs;
    private final String idempotencyKey;
    private final boolean async;
    private final String notes;
    private final Instant confirmedAt;
    private final UUID confirmedBy;
    private final Instant cancelledAt;
    private final UUID cancelledBy;
    private final String cancellationReason;
    private final Instant createdAt;
    private final Instant updatedAt;

    private BillingRunResponse(BillingRun r) {
        this.id = r.getId();
        this.propertyId = r.getPropertyId();
        this.billingMonthBs = r.getBillingMonthBs();
        this.periodStartBs = r.getPeriodStartBs();
        this.periodEndBs = r.getPeriodEndBs();
        this.status = r.getStatus().name();
        this.tariffMode = r.getTariffMode().name();
        this.tariffVersionId = r.getTariffVersionId();
        this.neaBlendedRate = r.getNeaBlendedRate();
        this.neaTotalBill = r.getNeaTotalBill();
        this.tenantCount = r.getTenantCount();
        this.totalBilled = r.getTotalBilled();
        this.generatedAtBs = r.getGeneratedAtBs();
        this.idempotencyKey = r.getIdempotencyKey();
        this.async = r.isAsync();
        this.notes = r.getNotes();
        this.confirmedAt = r.getConfirmedAt();
        this.confirmedBy = r.getConfirmedBy();
        this.cancelledAt = r.getCancelledAt();
        this.cancelledBy = r.getCancelledBy();
        this.cancellationReason = r.getCancellationReason();
        this.createdAt = r.getCreatedAt();
        this.updatedAt = r.getUpdatedAt();
    }

    public static BillingRunResponse from(BillingRun r) { return new BillingRunResponse(r); }
}
