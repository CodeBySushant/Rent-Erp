package com.renterp.domain.billing.entity;

import com.renterp.common.entity.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A dated NEA electricity tariff schedule (national reference data, not property-scoped).
 * The blended-rate engine (pass 2) reads {@link #slabs} to convert a property's main-meter
 * units into an NEA total, then divides to a blended per-unit rate. Effective-dated so a
 * historical bill reprices against the schedule in force then (spec B5 — no retroactive
 * repricing).
 */
@Entity
@Table(name = "tariff_versions")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class TariffVersion extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "effective_from_bs", nullable = false, length = 20)
    private String effectiveFromBs;

    // NULL = still in force.
    @Column(name = "effective_to_bs", length = 20)
    private String effectiveToBs;

    // Ordered ascending by uptoUnits; the last slab has uptoUnits = null (open-ended).
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<Slab> slabs = new ArrayList<>();

    @Column(name = "demand_charge", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal demandCharge = BigDecimal.ZERO;

    @Column(name = "service_charge", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal serviceCharge = BigDecimal.ZERO;

    @Column(name = "minimum_charge", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal minimumCharge = BigDecimal.ZERO;

    @Column(name = "vat_percent", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal vatPercent = new BigDecimal("13.00");

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    /** One NEA tariff slab. uptoUnits == null means "and above" (final slab). */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Slab {
        private Integer uptoUnits;        // inclusive upper bound of this slab; null = open-ended
        private BigDecimal ratePerUnit;
    }
}
