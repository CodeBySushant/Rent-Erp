package com.renterp.domain.meter.entity;

import com.renterp.common.entity.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

@Entity
@Table(name = "infrastructure_meter_scope")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class InfrastructureMeterScope extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "meter_id", nullable = false, columnDefinition = "uuid")
    private UUID meterId;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_by", nullable = false, length = 20)
    private ScopeBy scopeBy;

    // Non-null when scopeBy = FLOOR (DB-enforced in V8's chk_infra_scope_target).
    @Column(name = "floor_id", columnDefinition = "uuid")
    private UUID floorId;

    // Non-null when scopeBy = TENANT. MeterController defers TENANT-scoped rows to Phase 4.
    @Column(name = "tenant_id", columnDefinition = "uuid")
    private UUID tenantId;

    public enum ScopeBy {
        FLOOR, TENANT
    }
}
