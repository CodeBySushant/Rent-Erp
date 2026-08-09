package com.renterp.domain.charge.entity;

import com.renterp.common.entity.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "charge_templates")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class ChargeTemplate extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "property_id", nullable = false, columnDefinition = "uuid")
    private UUID propertyId;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "split_basis", nullable = false, length = 50)
    private SplitBasis splitBasis;

    // Spec §14.2 B6 — landlord's explicit confirmation that a zero amount is intentional.
    @Column(name = "zero_amount_acknowledged", nullable = false)
    @Builder.Default
    private boolean zeroAmountAcknowledged = false;

    // Spec §14.2 B7 — set only at deactivation time; null while the charge is active.
    @Enumerated(EnumType.STRING)
    @Column(name = "deactivation_mode", length = 50)
    private DeactivationMode deactivationMode;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    // createdAt / updatedAt are inherited from BaseAuditEntity and set by JPA auditing

    public enum SplitBasis {
        FIXED_PER_TENANT, SHARED
    }

    public enum DeactivationMode {
        THIS_CYCLE_PRORATED, NEXT_CYCLE, VOID
    }
}
