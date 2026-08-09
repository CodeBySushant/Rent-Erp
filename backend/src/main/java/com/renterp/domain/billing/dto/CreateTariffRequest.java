package com.renterp.domain.billing.dto;

import com.renterp.domain.billing.entity.TariffVersion;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class CreateTariffRequest {

    @NotBlank
    private String name;

    @NotBlank
    @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "effectiveFromBs must be a BS date YYYY-MM-DD")
    private String effectiveFromBs;

    @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "effectiveToBs must be a BS date YYYY-MM-DD")
    private String effectiveToBs;   // optional; null = still in force

    @Valid
    @NotNull
    private List<SlabInput> slabs = new ArrayList<>();

    @NotNull @DecimalMin("0.0")
    private BigDecimal demandCharge = BigDecimal.ZERO;

    @NotNull @DecimalMin("0.0")
    private BigDecimal serviceCharge = BigDecimal.ZERO;

    @NotNull @DecimalMin("0.0")
    private BigDecimal minimumCharge = BigDecimal.ZERO;

    @NotNull @DecimalMin("0.0")
    private BigDecimal vatPercent = new BigDecimal("13.00");

    private String notes;

    @Getter
    @Setter
    public static class SlabInput {
        // null = open-ended final slab ("and above")
        @Positive
        private Integer uptoUnits;

        @NotNull @DecimalMin("0.0")
        private BigDecimal ratePerUnit;

        public TariffVersion.Slab toSlab() {
            return new TariffVersion.Slab(uptoUnits, ratePerUnit);
        }
    }
}
