package com.renterp.domain.tenantfinance.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class UpdateDepositRequest {

    // Correction path only — amount / receivedAtBs / notes. status transitions are
    // vacancy operations (Phase 7) and go through a separate endpoint later.

    @DecimalMin(value = "0.00", message = "amount cannot be negative")
    private BigDecimal amount;

    @Pattern(regexp = "^[0-9]{4}-[0-9]{2}-[0-9]{2}$",
             message = "receivedAtBs must be YYYY-MM-DD BS format")
    @Size(max = 20)
    private String receivedAtBs;

    private String notes;
}
