package com.renterp.domain.tenancy.entity;

import com.renterp.common.entity.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "room_assignments")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class RoomAssignment extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "membership_id", nullable = false, columnDefinition = "uuid")
    private UUID membershipId;

    @Column(name = "room_id", nullable = false, columnDefinition = "uuid")
    private UUID roomId;

    @Column(name = "effective_from_bs", nullable = false, length = 20)
    private String effectiveFromBs;

    // NULL = currently active.
    @Column(name = "effective_to_bs", length = 20)
    private String effectiveToBs;

    // Per-tenant per-room rent (§10.5 / T14 storage). NULL for rows created before
    // V11 (tenancy-pass test data left as-is per user directive). New assignments
    // created via POST /memberships/{id}/room-assignments must specify a value —
    // enforced in CreateRoomAssignmentRequest + MembershipService.assignRoom.
    // Mutated (with an appended rent_increments row) on POST /rent-increments.
    @Column(name = "monthly_rent", precision = 10, scale = 2)
    private BigDecimal monthlyRent;
}
