package com.renterp.domain.billing.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Request to generate a billing run for a property over one BS period.
 *
 * <p>Implements {@link Serializable} so the B15 async worker path can carry it as
 * db-scheduler task data.</p>
 *
 * <p>The covered window ({@link #periodStartBs} … {@link #periodEndBs}) is supplied
 * explicitly rather than derived from the property's billing day — the billing-day-window
 * derivation lands in pass 2 alongside the segment engine. {@link #billingMonthBs} is the
 * "YYYY-MM" period label used for the one-live-run-per-period guard (B10).
 *
 * <p>Electricity/water amount inputs are only read for the non-metered modes handled in
 * pass 1; SUB_METERED / NEA-blended / KUKL / boring modes are rejected until pass 2.
 */
@Getter
@Setter
public class CreateBillingRunRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank
    @Pattern(regexp = "\\d{4}-\\d{2}", message = "billingMonthBs must be a BS year-month YYYY-MM")
    private String billingMonthBs;

    @NotBlank
    @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "periodStartBs must be a BS date YYYY-MM-DD")
    private String periodStartBs;

    @NotBlank
    @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "periodEndBs must be a BS date YYYY-MM-DD")
    private String periodEndBs;

    @NotBlank
    @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "generatedAtBs must be a BS date YYYY-MM-DD")
    private String generatedAtBs;

    // B10 layer 1 — optional client idempotency key; a retry with the same key returns the
    // existing run rather than creating a duplicate.
    @Size(max = 100)
    private String idempotencyKey;

    // MAIN_METER_ONLY electricity: the property's total electricity cost to split this period.
    @DecimalMin("0.0")
    private BigDecimal electricityTotalAmount;

    // FIXED_PER_TENANT electricity: flat per-tenant amount (prorated by occupancy).
    @DecimalMin("0.0")
    private BigDecimal electricityFixedAmount;

    // FIXED_PER_TENANT water: flat per-tenant amount (prorated by occupancy).
    @DecimalMin("0.0")
    private BigDecimal waterFixedAmount;

    // ── Pass 2 inputs ────────────────────────────────────────────────────────

    // SUB_METERED + FLAT_RATE electricity: the landlord's per-unit price. Ignored for
    // BLENDED_RATE (the rate is derived from the main meter + tariff slabs).
    @DecimalMin("0.0")
    private BigDecimal electricityRatePerUnit;

    // KUKL_SPLIT / KUKL_AND_BORING water: the property's variable monthly KUKL bill to split.
    @DecimalMin("0.0")
    private BigDecimal waterKuklAmount;

    // BORING_PUMP_ONLY / KUKL_AND_BORING water: the pump running cost to split.
    @DecimalMin("0.0")
    private BigDecimal waterBoringAmount;

    // CUSTOM split rule: membershipId → weight. Required (and must cover every billable
    // membership) when the property's split rule is CUSTOM.
    private Map<UUID, BigDecimal> customWeights;

    // B15: force the async worker path regardless of tenant count (testing / explicit choice).
    // When null the engine decides by tenant count vs the async threshold.
    private Boolean async;

    private String notes;
}
