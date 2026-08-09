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
    private final Instant createdAt;
    private final Instant updatedAt;

    private RoomResponse(Room room) {
        this.id = room.getId();
        this.floorId = room.getFloorId();
        this.name = room.getName();
        this.active = room.isActive();
        this.createdAt = room.getCreatedAt();
        this.updatedAt = room.getUpdatedAt();
    }

    public static RoomResponse from(Room room) {
        return new RoomResponse(room);
    }
}
