package com.renterp.domain.tenantfinance.entity;

import com.renterp.common.entity.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "rent_increments")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class RentIncrement extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "membership_id", nullable = false, columnDefinition = "uuid")
    private UUID membershipId;

    @Column(name = "room_assignment_id", nullable = false, columnDefinition = "uuid")
    private UUID roomAssignmentId;

    // NULL when the assignment had no prior rent set (rare; only for rows created before
    // V11 which had monthlyRent=NULL). Captured by the service at apply time from the
    // current assignment.monthlyRent, never accepted from the client.
    @Column(name = "previous_amount", precision = 10, scale = 2)
    private BigDecimal previousAmount;

    @Column(name = "new_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal newAmount;

    @Column(name = "effective_bs", nullable = false, length = 20)
    private String effectiveBs;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(name = "notified_tenant", nullable = false)
    @Builder.Default
    private boolean notifiedTenant = false;

    @Column(name = "changed_by", columnDefinition = "uuid")
    private UUID changedBy;
}
