package com.renterp.domain.tenantfinance.dto;

import com.renterp.domain.tenantfinance.entity.TenantOpeningBalance.Direction;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class CreateOpeningBalanceRequest {

    @NotNull @DecimalMin(value = "0.00", message = "amount cannot be negative")
    private BigDecimal amount;

    @NotNull(message = "direction is required (OWED_BY_TENANT or CREDIT_TO_TENANT)")
    private Direction direction;

    @NotBlank
    @Pattern(regexp = "^[0-9]{4}-[0-9]{2}-[0-9]{2}$")
    @Size(max = 20)
    private String asOfBs;

    private String notes;
}
