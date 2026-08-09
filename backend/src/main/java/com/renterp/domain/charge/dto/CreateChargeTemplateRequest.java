package com.renterp.domain.charge.dto;

import com.renterp.domain.charge.entity.ChargeTemplate.SplitBasis;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
public class CreateChargeTemplateRequest {

    @NotNull(message = "Property id is required")
    private UUID propertyId;

    @NotBlank(message = "Charge name is required")
    @Size(max = 255, message = "Name must not exceed 255 characters")
    private String name;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.00", message = "Amount cannot be negative")
    private BigDecimal amount;

    @NotNull(message = "Split basis is required")
    private SplitBasis splitBasis;

    // Spec §14.2 B6 — must be explicitly true when amount is 0.00, checked in the service
    // (cross-field, so not a bean-validation annotation). Defaults false, i.e. "not yet confirmed".
    private boolean zeroAmountAcknowledged = false;
}
