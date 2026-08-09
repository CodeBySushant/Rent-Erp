package com.renterp.domain.meter.dto;

import com.renterp.domain.meter.entity.Meter.InfrastructureScopeType;
import com.renterp.domain.meter.entity.Meter.MeterPurpose;
import com.renterp.domain.meter.entity.Meter.MeterType;
import com.renterp.domain.meter.entity.Meter.ReadingResponsibility;
import com.renterp.domain.meter.entity.Meter.SplitRule;
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
public class CreateMeterRequest {

    @NotNull(message = "Property id is required")
    private UUID propertyId;

    // Optional — spec §14.1 M12 dedup only fires when serial is present.
    @Size(max = 100, message = "Serial number must not exceed 100 characters")
    private String serialNumber;

    @NotBlank(message = "Label is required")
    @Size(max = 255, message = "Label must not exceed 255 characters")
    private String label;

    @NotNull(message = "Meter purpose is required")
    private MeterPurpose meterPurpose;

    @NotNull(message = "Meter type is required")
    private MeterType meterType;

    // Required when meterType = INFRASTRUCTURE, must be null otherwise. Enforced in the service.
    private InfrastructureScopeType infrastructureScopeType;

    // NULL = fall back to property.defaultSplitRule at billing time.
    private SplitRule splitRuleOverride;

    // Spec §7.6. Service defaults infra meters to LANDLORD_ONLY when omitted (§14.1 M18).
    private ReadingResponsibility readingResponsibility;

    // §14.1 M1 rollover math uses this per-meter cap. Optional; defaults to 99999 at
    // the entity layer if omitted.
    @DecimalMin(value = "1.00", message = "maxReadingValue must be positive")
    private BigDecimal maxReadingValue;
}
