package com.renterp.domain.tenantfinance.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class CreateAdvanceRentRequest {

    @NotNull @DecimalMin(value = "0.01", message = "amount must be positive")
    private BigDecimal amount;

    @NotNull @Min(value = 1, message = "monthsCovered must be at least 1")
    private Short monthsCovered;

    @NotBlank
    @Pattern(regexp = "^[0-9]{4}-[0-9]{2}-[0-9]{2}$")
    @Size(max = 20)
    private String coveredFromBs;

    @NotBlank
    @Pattern(regexp = "^[0-9]{4}-[0-9]{2}-[0-9]{2}$")
    @Size(max = 20)
    private String coveredToBs;

    private String notes;
}
