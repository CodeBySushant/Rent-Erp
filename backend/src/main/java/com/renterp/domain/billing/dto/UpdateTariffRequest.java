package com.renterp.domain.billing.dto;

import com.renterp.domain.billing.dto.CreateTariffRequest.SlabInput;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

/**
 * Correction of a tariff version. Permitted only while the version is not yet referenced by
 * any CONFIRMED billing run (enforced in the service) — an in-force schedule that a bill was
 * already priced against is immutable (B5, no retroactive repricing).
 */
@Getter
@Setter
public class UpdateTariffRequest {

    @NotBlank
    private String name;

    @NotBlank
    @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "effectiveFromBs must be a BS date YYYY-MM-DD")
    private String effectiveFromBs;

    @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "effectiveToBs must be a BS date YYYY-MM-DD")
    private String effectiveToBs;

    @Valid
    @NotNull
    private List<SlabInput> slabs;

    @NotNull @DecimalMin("0.0")
    private BigDecimal demandCharge;

    @NotNull @DecimalMin("0.0")
    private BigDecimal serviceCharge;

    @NotNull @DecimalMin("0.0")
    private BigDecimal minimumCharge;

    @NotNull @DecimalMin("0.0")
    private BigDecimal vatPercent;

    private String notes;
}
