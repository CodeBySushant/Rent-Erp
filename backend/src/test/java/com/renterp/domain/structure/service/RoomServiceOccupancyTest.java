package com.renterp.domain.structure.service;

import com.renterp.domain.meter.repository.MeterRoomCoverageRepository;
import com.renterp.domain.structure.dto.RoomResponse;
import com.renterp.domain.structure.entity.Room;
import com.renterp.domain.structure.repository.FloorRepository;
import com.renterp.domain.structure.repository.RoomRepository;
import com.renterp.domain.tenancy.entity.TenantPropertyMembership.MembershipStatus;
import com.renterp.domain.tenancy.repository.RoomAssignmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Room lists say which rooms have a current tenant (property screen Vacant / Occupied filters). */
class RoomServiceOccupancyTest {

    private RoomRepository rooms;
    private RoomAssignmentRepository assignments;
    private RoomService service;
    private final UUID floorId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        rooms = mock(RoomRepository.class);
        assignments = mock(RoomAssignmentRepository.class);
        service = new RoomService(rooms, mock(FloorRepository.class), assignments,
                mock(MeterRoomCoverageRepository.class), mock(com.renterp.common.guard.PropertyGuard.class));
    }

    private static Room room(String name) {
        Room r = new Room();
        r.setId(UUID.randomUUID());
        r.setName(name);
        r.setActive(true);
        return r;
    }

    @Test
    void marksOccupiedAndVacantRoomsInOneQuery() {
        Room taken = room("101");
        Room free = room("102");
        var pageable = PageRequest.of(0, 20);
        Page<Room> page = new PageImpl<>(List.of(taken, free), pageable, 2);
        when(rooms.findByFloorId(floorId, pageable)).thenReturn(page);
        when(assignments.findOccupiedRoomIds(anyCollection(), eq(MembershipStatus.ACTIVE)))
                .thenReturn(List.of(taken.getId()));

        Map<String, Boolean> byName = service.getAllRooms(floorId, null, pageable).getContent().stream()
                .collect(Collectors.toMap(RoomResponse::getName, RoomResponse::getOccupied));

        assertEquals(Boolean.TRUE, byName.get("101"));
        assertEquals(Boolean.FALSE, byName.get("102"));
    }

    @Test
    void anEmptyPageSkipsTheQuery() {
        var pageable = PageRequest.of(0, 20);
        when(rooms.findByFloorId(floorId, pageable)).thenReturn(new PageImpl<>(List.of(), pageable, 0));

        assertTrue(service.getAllRooms(floorId, null, pageable).getContent().isEmpty());
        verify(assignments, never()).findOccupiedRoomIds(anyCollection(), any());
    }

    @Test
    void singleRoomCarriesItsOccupancy() {
        Room free = room("201");
        when(rooms.findById(free.getId())).thenReturn(Optional.of(free));
        when(assignments.findOccupiedRoomIds(anyCollection(), eq(MembershipStatus.ACTIVE)))
                .thenReturn(List.of());

        assertFalse(service.getRoomById(free.getId()).getOccupied());
    }
}
