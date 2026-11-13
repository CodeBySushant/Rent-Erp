package com.renterp.domain.structure.dto;

import com.renterp.domain.structure.entity.Room;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
public class RoomResponse {

    private final UUID id;
    private final UUID floorId;
    private final String name;
    private final boolean active;

    /**
     * True when the room has a current tenant (an open assignment on an ACTIVE
     * membership). Null where it was not worked out; the list endpoint
     * always fills it.
     */
    private final Boolean occupied;
    private final Instant createdAt;
    private final Instant updatedAt;

    private RoomResponse(Room room, Boolean occupied) {
        this.occupied = occupied;
        this.id = room.getId();
        this.floorId = room.getFloorId();
        this.name = room.getName();
        this.active = room.isActive();
        this.createdAt = room.getCreatedAt();
        this.updatedAt = room.getUpdatedAt();
    }

    public static RoomResponse from(Room room) {
        return new RoomResponse(room, null);
    }

    public static RoomResponse from(Room room, boolean occupied) {
        return new RoomResponse(room, occupied);
    }
}
