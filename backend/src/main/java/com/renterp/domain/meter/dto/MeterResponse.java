package com.renterp.domain.meter.dto;

import com.renterp.domain.meter.entity.Meter;
import com.renterp.domain.meter.entity.Meter.InfrastructureScopeType;
import com.renterp.domain.meter.entity.Meter.MeterPurpose;
import com.renterp.domain.meter.entity.Meter.MeterStatus;
import com.renterp.domain.meter.entity.Meter.MeterType;
import com.renterp.domain.meter.entity.Meter.ReadingResponsibility;
import com.renterp.domain.meter.entity.Meter.SplitRule;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
public class MeterResponse {

    private final UUID id;
    private final UUID propertyId;
    private final String serialNumber;
    private final String label;
    private final MeterPurpose meterPurpose;
    private final MeterType meterType;
    private final InfrastructureScopeType infrastructureScopeType;
    private final SplitRule splitRuleOverride;
    private final ReadingResponsibility readingResponsibility;
    private final UUID designatedTenantId;
    private final UUID replacedByMeterId;
    private final BigDecimal maxReadingValue;
    private final MeterStatus status;
    private final boolean active;
    private final Instant createdAt;
    private final Instant updatedAt;

    private MeterResponse(Meter m) {
        this.id = m.getId();
        this.propertyId = m.getPropertyId();
        this.serialNumber = m.getSerialNumber();
        this.label = m.getLabel();
        this.meterPurpose = m.getMeterPurpose();
        this.meterType = m.getMeterType();
        this.infrastructureScopeType = m.getInfrastructureScopeType();
        this.splitRuleOverride = m.getSplitRuleOverride();
        this.readingResponsibility = m.getReadingResponsibility();
        this.designatedTenantId = m.getDesignatedTenantId();
        this.replacedByMeterId = m.getReplacedByMeterId();
        this.maxReadingValue = m.getMaxReadingValue();
        this.status = m.getStatus();
        this.active = m.isActive();
        this.createdAt = m.getCreatedAt();
        this.updatedAt = m.getUpdatedAt();
    }

    public static MeterResponse from(Meter m) {
        return new MeterResponse(m);
    }
}
