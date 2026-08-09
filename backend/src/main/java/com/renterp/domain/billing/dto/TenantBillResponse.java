package com.renterp.domain.billing.dto;

import com.renterp.domain.billing.entity.TenantBill;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
public class TenantBillResponse {

    private final UUID id;
    private final UUID billingRunId;
    private final UUID propertyId;
    private final UUID membershipId;
    private final String billingMonthBs;
    private final String periodStartBs;
    private final String periodEndBs;
    private final int daysOccupied;
    private final int daysInPeriod;
    private final boolean prorated;

    private final BigDecimal rentAmount;
    private final BigDecimal electricityAmount;
    private final BigDecimal waterAmount;
    private final BigDecimal chargesAmount;
    private final BigDecimal penaltyAmount;
    private final BigDecimal adjustmentsAmount;
    private final BigDecimal advanceAppliedAmount;
    private final BigDecimal previousBalance;
    private final BigDecimal subtotal;
    private final BigDecimal tdsAmount;
    private final BigDecimal roundingAdjustment;
    private final BigDecimal totalDue;
    private final BigDecimal amountPaid;
    private final BigDecimal balanceDue;
    private final String paymentStatus;

    private final List<BillLineItem> lineItems;
    private final String status;
    private final String generatedAtBs;
    private final short gracePeriodDays;
    private final String dueDateBs;
    private final String notes;
    private final UUID supersedesBillId;
    private final UUID supersededByBillId;
    private final Instant createdAt;
    private final Instant updatedAt;

    private TenantBillResponse(TenantBill b) {
        this.id = b.getId();
        this.billingRunId = b.getBillingRunId();
        this.propertyId = b.getPropertyId();
        this.membershipId = b.getMembershipId();
        this.billingMonthBs = b.getBillingMonthBs();
        this.periodStartBs = b.getPeriodStartBs();
        this.periodEndBs = b.getPeriodEndBs();
        this.daysOccupied = b.getDaysOccupied();
        this.daysInPeriod = b.getDaysInPeriod();
        this.prorated = b.isProrated();
        this.rentAmount = b.getRentAmount();
        this.electricityAmount = b.getElectricityAmount();
        this.waterAmount = b.getWaterAmount();
        this.chargesAmount = b.getChargesAmount();
        this.penaltyAmount = b.getPenaltyAmount();
        this.adjustmentsAmount = b.getAdjustmentsAmount();
        this.advanceAppliedAmount = b.getAdvanceAppliedAmount();
        this.previousBalance = b.getPreviousBalance();
        this.subtotal = b.getSubtotal();
        this.tdsAmount = b.getTdsAmount();
        this.roundingAdjustment = b.getRoundingAdjustment();
        this.totalDue = b.getTotalDue();
        this.amountPaid = b.getAmountPaid();
        this.balanceDue = b.getBalanceDue();
        this.paymentStatus = b.getPaymentStatus().name();
        this.lineItems = b.getLineItems();
        this.status = b.getStatus().name();
        this.generatedAtBs = b.getGeneratedAtBs();
        this.gracePeriodDays = b.getGracePeriodDays();
        this.dueDateBs = b.getDueDateBs();
        this.notes = b.getNotes();
        this.supersedesBillId = b.getSupersedesBillId();
        this.supersededByBillId = b.getSupersededByBillId();
        this.createdAt = b.getCreatedAt();
        this.updatedAt = b.getUpdatedAt();
    }

    public static TenantBillResponse from(TenantBill b) { return new TenantBillResponse(b); }
}
