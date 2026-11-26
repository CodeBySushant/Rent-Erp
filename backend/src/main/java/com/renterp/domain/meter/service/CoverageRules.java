package com.renterp.domain.meter.service;

import com.renterp.common.exception.InvalidOperationException;
import com.renterp.common.exception.ResourceNotFoundException;
import com.renterp.domain.meter.entity.Meter;
import com.renterp.domain.structure.entity.Floor;
import com.renterp.domain.structure.entity.Room;
import com.renterp.domain.structure.repository.FloorRepository;
import com.renterp.domain.structure.repository.RoomRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * H15: the rules every new meter-to-room link must pass, shared by direct
 * coverage (MeterService) and coverage-change events (CoverageEventService):
 * <ul>
 *   <li>the room exists, is active, and is in the meter's own property.</li>
 * </ul>
 * A room may be covered by any number of sub-meters (e.g. a lights meter and an
 * AC meter). ElectricityEngine bills each meter's own units separately and adds
 * them up per tenant, so nothing is billed twice.
 * The same meter cannot cover the same room twice at once (MeterService checks that).
 */
@Component
public class CoverageRules {

    private final RoomRepository rooms;
    private final FloorRepository floors;

    public CoverageRules(RoomRepository rooms, FloorRepository floors) {
        this.rooms = rooms;
        this.floors = floors;
    }

    public void requireCoverable(Meter meter, UUID roomId) {
        Room room = rooms.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Room", "id", roomId));
        Floor floor = floors.findById(room.getFloorId())
                .orElseThrow(() -> new ResourceNotFoundException("Floor", "id", room.getFloorId()));
        if (!floor.getPropertyId().equals(meter.getPropertyId())) {
            throw new InvalidOperationException("ROOM_NOT_IN_PROPERTY",
                    "Room " + room.getName() + " belongs to a different property than this meter.");
        }
        if (!room.isActive() || !floor.isActive()) {
            throw new InvalidOperationException("ROOM_INACTIVE", "Room " + room.getName() + " has been removed.");
        }
    }
}
