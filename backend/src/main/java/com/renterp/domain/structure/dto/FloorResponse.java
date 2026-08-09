package com.renterp.domain.structure.dto;

import com.renterp.domain.structure.entity.Floor;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
public class FloorResponse {

    private final UUID id;
    private final UUID propertyId;
    private final String name;
    private final short floorNumber;
    private final boolean active;
    private final Instant createdAt;
    private final Instant updatedAt;

    private FloorResponse(Floor floor) {
        this.id = floor.getId();
        this.propertyId = floor.getPropertyId();
        this.name = floor.getName();
        this.floorNumber = floor.getFloorNumber();
        this.active = floor.isActive();
        this.createdAt = floor.getCreatedAt();
        this.updatedAt = floor.getUpdatedAt();
    }

    public static FloorResponse from(Floor floor) {
        return new FloorResponse(floor);
    }
}
