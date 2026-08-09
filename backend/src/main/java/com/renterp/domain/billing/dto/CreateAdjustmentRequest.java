package com.renterp.domain.billing.dto;

import com.renterp.domain.billing.entity.TenantBillAdjustment.AdjustmentType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Records a one-time charge or credit that follows the tenant onto their next bill — incl.
 * the vacancy bill (P9). Source is fixed to ONE_TIME here; BILL_CORRECTION and OVERPAYMENT
 * adjustments are created by their own flows (pass 2 / Payment phase).
 */
@Getter
@Setter
public class CreateAdjustmentRequest {

    @NotNull
    private AdjustmentType adjustmentType;   // CHARGE | CREDIT

    @NotNull
    @DecimalMin(value = "0.01", message = "amount must be positive")
    private BigDecimal amount;

    private String reason;

    private UUID createdBy;
}
