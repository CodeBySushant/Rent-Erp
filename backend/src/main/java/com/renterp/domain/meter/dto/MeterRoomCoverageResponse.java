package com.renterp.domain.meter.dto;

import com.renterp.domain.meter.entity.MeterRoomCoverage;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
public class MeterRoomCoverageResponse {

    private final UUID id;
    private final UUID meterId;
    private final UUID roomId;
    private final String effectiveFromBs;
    private final String effectiveToBs;
    private final Instant createdAt;
    private final Instant updatedAt;

    private MeterRoomCoverageResponse(MeterRoomCoverage c) {
        this.id = c.getId();
        this.meterId = c.getMeterId();
        this.roomId = c.getRoomId();
        this.effectiveFromBs = c.getEffectiveFromBs();
        this.effectiveToBs = c.getEffectiveToBs();
        this.createdAt = c.getCreatedAt();
        this.updatedAt = c.getUpdatedAt();
    }

    public static MeterRoomCoverageResponse from(MeterRoomCoverage c) {
        return new MeterRoomCoverageResponse(c);
    }
}
