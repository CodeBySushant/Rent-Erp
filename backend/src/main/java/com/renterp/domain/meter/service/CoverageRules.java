package com.renterp.domain.meter.service;

import com.renterp.common.exception.InvalidOperationException;
import com.renterp.common.exception.ResourceNotFoundException;
import com.renterp.domain.meter.entity.Meter;
import com.renterp.domain.meter.entity.MeterRoomCoverage;
import com.renterp.domain.meter.repository.MeterRoomCoverageRepository;
import com.renterp.domain.structure.entity.Floor;
import com.renterp.domain.structure.entity.Room;
import com.renterp.domain.structure.repository.FloorRepository;
import com.renterp.domain.structure.repository.RoomRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * H15: the rules every new meter-to-room link must pass, shared by direct
 * coverage (MeterService) and coverage-change events (CoverageEventService):
 * <ul>
 *   <li>the room exists, is active, and is in the meter's own property;</li>
 *   <li>no other active sub-meter of the same purpose already covers the room
 *       (two electricity sub-meters on one room would bill it twice).</li>
 * </ul>
 */
@Component
public class CoverageRules {

    private final RoomRepository rooms;
    private final FloorRepository floors;
    private final MeterRoomCoverageRepository coverage;

    public CoverageRules(RoomRepository rooms, FloorRepository floors, MeterRoomCoverageRepository coverage) {
        this.rooms = rooms;
        this.floors = floors;
        this.coverage = coverage;
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
        List<MeterRoomCoverage> open = coverage.findOpenCoverageOfRoom(roomId, meter.getMeterPurpose(),
                Meter.MeterType.TENANT_SUPPLY);
        for (MeterRoomCoverage c : open) {
            if (!c.getMeterId().equals(meter.getId())) {
                throw new InvalidOperationException("ROOM_ALREADY_METERED",
                        "Room " + room.getName() + " is already covered by another meter. "
                                + "End that meter's coverage of the room first.");
            }
        }
    }
}
