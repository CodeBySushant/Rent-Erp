package com.renterp.domain.meter.entity;

import com.renterp.common.entity.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "meters")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class Meter extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "property_id", nullable = false, columnDefinition = "uuid")
    private UUID propertyId;

    @Column(name = "serial_number", length = 100)
    private String serialNumber;

    @Column(nullable = false, length = 255)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(name = "meter_purpose", nullable = false, length = 30)
    private MeterPurpose meterPurpose;

    @Enumerated(EnumType.STRING)
    @Column(name = "meter_type", nullable = false, length = 30)
    private MeterType meterType;

    // Non-null only when meterType = INFRASTRUCTURE (DB-enforced in V8's chk_infra_scope_matches_type).
    @Enumerated(EnumType.STRING)
    @Column(name = "infrastructure_scope_type", length = 20)
    private InfrastructureScopeType infrastructureScopeType;

    // NULL = fall back to Property.defaultSplitRule at billing time.
    @Enumerated(EnumType.STRING)
    @Column(name = "split_rule_override", length = 20)
    private SplitRule splitRuleOverride;

    // Spec §7.6 / §14.1 M18.
    @Enumerated(EnumType.STRING)
    @Column(name = "reading_responsibility", nullable = false, length = 30)
    private ReadingResponsibility readingResponsibility;

    // Populated in Phase 4 once tenants exist; nullable meanwhile.
    @Column(name = "designated_tenant_id", columnDefinition = "uuid")
    private UUID designatedTenantId;

    // Populated by MeterReadingController's replacement-event workflow (§7.8/§14.1 M4).
    @Column(name = "replaced_by_meter_id", columnDefinition = "uuid")
    private UUID replacedByMeterId;

    // Per-meter max display value used by rollover math (§14.1 M1). Default 99999 fits
    // the typical 5-digit electricity meter; landlord can override per meter.
    @Column(name = "max_reading_value", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal maxReadingValue = new BigDecimal("99999");

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private MeterStatus status = MeterStatus.ACTIVE;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    public enum MeterPurpose {
        ELECTRICITY, WATER
    }

    public enum MeterType {
        MAIN, TENANT_SUPPLY, INFRASTRUCTURE
    }

    public enum InfrastructureScopeType {
        GLOBAL, SCOPED
    }

    // Same three values as Property.SplitRule — deliberately duplicated so the meter
    // domain doesn't force a compile-time dependency on Property just for an enum.
    public enum SplitRule {
        EQUAL, ROOM_WEIGHTED, CUSTOM
    }

    public enum ReadingResponsibility {
        FIRST_SUBMISSION_WINS, DESIGNATED_TENANT, LANDLORD_ONLY
    }

    public enum MeterStatus {
        ACTIVE, INACTIVE
    }
}
