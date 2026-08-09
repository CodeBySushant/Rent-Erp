package com.renterp.domain.tenantfinance.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class CreateDepositRequest {

    @NotNull(message = "amount is required")
    @DecimalMin(value = "0.00", message = "amount cannot be negative")
    private BigDecimal amount;

    // Optional; defaults to NPR at the entity layer.
    @Pattern(regexp = "^[A-Z]{3}$", message = "currency must be a 3-letter ISO code")
    private String currency;

    @NotBlank(message = "receivedAtBs is required")
    @Pattern(regexp = "^[0-9]{4}-[0-9]{2}-[0-9]{2}$",
             message = "receivedAtBs must be YYYY-MM-DD BS format")
    @Size(max = 20)
    private String receivedAtBs;

    private String notes;
}
