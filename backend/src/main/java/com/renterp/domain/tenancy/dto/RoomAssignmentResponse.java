package com.renterp.domain.tenancy.dto;

import com.renterp.domain.tenancy.entity.RoomAssignment;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
public class RoomAssignmentResponse {

    private final UUID id;
    private final UUID membershipId;
    private final UUID roomId;
    private final String effectiveFromBs;
    private final String effectiveToBs;
    private final BigDecimal monthlyRent;
    private final Instant createdAt;
    private final Instant updatedAt;

    private RoomAssignmentResponse(RoomAssignment r) {
        this.id = r.getId();
        this.membershipId = r.getMembershipId();
        this.roomId = r.getRoomId();
        this.effectiveFromBs = r.getEffectiveFromBs();
        this.effectiveToBs = r.getEffectiveToBs();
        this.monthlyRent = r.getMonthlyRent();
        this.createdAt = r.getCreatedAt();
        this.updatedAt = r.getUpdatedAt();
    }

    public static RoomAssignmentResponse from(RoomAssignment r) { return new RoomAssignmentResponse(r); }
}
