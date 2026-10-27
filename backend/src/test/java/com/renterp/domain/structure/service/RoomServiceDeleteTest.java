package com.renterp.domain.structure.service;

import com.renterp.common.exception.InvalidOperationException;
import com.renterp.common.exception.ResourceNotFoundException;
import com.renterp.domain.meter.repository.MeterRoomCoverageRepository;
import com.renterp.domain.structure.entity.Room;
import com.renterp.domain.structure.repository.FloorRepository;
import com.renterp.domain.structure.repository.RoomRepository;
import com.renterp.domain.tenancy.entity.RoomAssignment;
import com.renterp.domain.tenancy.repository.RoomAssignmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Deleting (deactivating) a room is refused while a tenant or a meter is still attached. */
class RoomServiceDeleteTest {

    private RoomRepository rooms;
    private RoomAssignmentRepository assignments;
    private MeterRoomCoverageRepository coverage;
    private RoomService service;
    private UUID roomId;
    private Room room;

    @BeforeEach
    void setUp() {
        rooms = mock(RoomRepository.class);
        assignments = mock(RoomAssignmentRepository.class);
        coverage = mock(MeterRoomCoverageRepository.class);
        service = new RoomService(rooms, mock(FloorRepository.class), assignments, coverage,
                mock(com.renterp.common.guard.PropertyGuard.class));
        roomId = UUID.randomUUID();
        room = new Room();
        room.setActive(true);
        when(rooms.findById(roomId)).thenReturn(Optional.of(room));
        when(assignments.findFirstByRoomIdAndEffectiveToBsIsNull(roomId)).thenReturn(Optional.empty());
        when(coverage.existsByRoomIdAndEffectiveToBsIsNull(roomId)).thenReturn(false);
    }

    @Test
    void deactivatesAnEmptyRoom() {
        service.deleteRoom(roomId);
        assertFalse(room.isActive());
        verify(rooms).saveAndFlush(room);
    }

    @Test
    void refusesWhileATenantIsAssigned() {
        when(assignments.findFirstByRoomIdAndEffectiveToBsIsNull(roomId))
                .thenReturn(Optional.of(mock(RoomAssignment.class)));
        assertThrows(InvalidOperationException.class, () -> service.deleteRoom(roomId));
        assertTrue(room.isActive());
        verify(rooms, never()).saveAndFlush(any());
    }

    @Test
    void refusesWhileAMeterCoversIt() {
        when(coverage.existsByRoomIdAndEffectiveToBsIsNull(roomId)).thenReturn(true);
        assertThrows(InvalidOperationException.class, () -> service.deleteRoom(roomId));
        assertTrue(room.isActive());
        verify(rooms, never()).saveAndFlush(any());
    }

    @Test
    void unknownRoomIsNotFound() {
        UUID other = UUID.randomUUID();
        when(rooms.findById(other)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.deleteRoom(other));
    }
}
