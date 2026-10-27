package com.renterp.domain.meter.dto;

import com.renterp.domain.meter.entity.Meter.ReadingResponsibility;
import com.renterp.domain.meter.entity.Meter.SplitRule;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class UpdateMeterRequest {

    // Serial can be edited (typo correction) — property-scoped uniqueness re-checked in service.
    @Size(max = 100, message = "Serial number must not exceed 100 characters")
    private String serialNumber;

    @Size(max = 255, message = "Label must not exceed 255 characters")
    private String label;

    // meterPurpose / meterType / infrastructureScopeType are intentionally NOT updatable.
    // Changing them mid-life invalidates every downstream billing calculation; the correct
    // operation is a formal replacement or a new meter, both handled by MeterReadingController.

    private SplitRule splitRuleOverride;

    private ReadingResponsibility readingResponsibility;

    @DecimalMin(value = "1.00", message = "maxReadingValue must be positive")
    private BigDecimal maxReadingValue;

    // M2: the tenant profile responsible for reading a DESIGNATED_TENANT meter.
    // Must have an active tenancy at the meter's property.
    private java.util.UUID designatedTenantId;

    // M2: true removes the designated tenant.
    private Boolean clearDesignatedTenant;
}
