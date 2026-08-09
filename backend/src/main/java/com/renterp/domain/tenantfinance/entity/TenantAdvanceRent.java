package com.renterp.domain.tenantfinance.entity;

import com.renterp.common.entity.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "tenant_advance_rent")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class TenantAdvanceRent extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "membership_id", nullable = false, columnDefinition = "uuid")
    private UUID membershipId;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "months_covered", nullable = false)
    private short monthsCovered;

    @Column(name = "covered_from_bs", nullable = false, length = 20)
    private String coveredFromBs;

    @Column(name = "covered_to_bs", nullable = false, length = 20)
    private String coveredToBs;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private AdvanceStatus status = AdvanceStatus.HELD;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    public enum AdvanceStatus { HELD, CONSUMED, REFUNDED }
}
