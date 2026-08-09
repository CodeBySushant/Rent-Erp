package com.renterp.domain.billing.dto;

import com.renterp.domain.billing.entity.TariffVersion;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
public class TariffResponse {

    private final UUID id;
    private final String name;
    private final String effectiveFromBs;
    private final String effectiveToBs;
    private final List<TariffVersion.Slab> slabs;
    private final BigDecimal demandCharge;
    private final BigDecimal serviceCharge;
    private final BigDecimal minimumCharge;
    private final BigDecimal vatPercent;
    private final String notes;
    private final boolean active;
    private final Instant createdAt;
    private final Instant updatedAt;

    private TariffResponse(TariffVersion t) {
        this.id = t.getId();
        this.name = t.getName();
        this.effectiveFromBs = t.getEffectiveFromBs();
        this.effectiveToBs = t.getEffectiveToBs();
        this.slabs = t.getSlabs();
        this.demandCharge = t.getDemandCharge();
        this.serviceCharge = t.getServiceCharge();
        this.minimumCharge = t.getMinimumCharge();
        this.vatPercent = t.getVatPercent();
        this.notes = t.getNotes();
        this.active = t.isActive();
        this.createdAt = t.getCreatedAt();
        this.updatedAt = t.getUpdatedAt();
    }

    public static TariffResponse from(TariffVersion t) { return new TariffResponse(t); }
}
