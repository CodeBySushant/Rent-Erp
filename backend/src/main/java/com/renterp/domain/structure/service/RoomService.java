package com.renterp.domain.structure.service;

import com.renterp.common.exception.DuplicateResourceException;
import com.renterp.common.exception.ResourceNotFoundException;
import com.renterp.domain.structure.dto.CreateRoomRequest;
import com.renterp.domain.structure.dto.RoomResponse;
import com.renterp.domain.structure.dto.UpdateRoomRequest;
import com.renterp.domain.structure.entity.Floor;
import com.renterp.domain.structure.entity.Room;
import com.renterp.domain.structure.repository.FloorRepository;
import com.renterp.domain.structure.repository.RoomRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class RoomService {

    private static final Logger log = LogManager.getLogger(RoomService.class);

    private final RoomRepository roomRepository;
    private final FloorRepository floorRepository;

    public RoomService(RoomRepository roomRepository, FloorRepository floorRepository) {
        this.roomRepository = roomRepository;
        this.floorRepository = floorRepository;
    }

    // ── Create ─────────────────────────────────────────────────────────────────

    @Transactional
    public RoomResponse createRoom(CreateRoomRequest request) {
        log.debug("Creating room — floor: {}, name: {}", request.getFloorId(), request.getName());

        if (!floorRepository.existsById(request.getFloorId())) {
            log.warn("Room creation failed — floor not found: {}", request.getFloorId());
            throw new ResourceNotFoundException("Floor", "id", request.getFloorId());
        }
        if (roomRepository.existsByFloorIdAndName(request.getFloorId(), request.getName())) {
            log.warn("Room creation failed — name '{}' already exists on floor {}",
                    request.getName(), request.getFloorId());
            throw new DuplicateResourceException("Room", "floorId+name",
                    request.getFloorId() + "+" + request.getName());
        }

        Room room = Room.builder()
                .floorId(request.getFloorId())
                .name(request.getName())
                .build();

        Room saved = roomRepository.save(room);
        log.info("Room created successfully — id: {}, floor: {}, name: {}",
                saved.getId(), saved.getFloorId(), saved.getName());

        return RoomResponse.from(saved);
    }

    // ── Read single ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public RoomResponse getRoomById(UUID id) {
        log.debug("Fetching room by id: {}", id);

        Room room = roomRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Room not found — id: {}", id);
                    return new ResourceNotFoundException("Room", "id", id);
                });

        return RoomResponse.from(room);
    }

    // ── Read all (paginated, filtered by floor or by property) ─────────────────

    @Transactional(readOnly = true)
    public Page<RoomResponse> getAllRooms(UUID floorId, UUID propertyId, Pageable pageable) {
        log.debug("Fetching rooms — floor: {}, property: {}, page: {}, size: {}",
                floorId, propertyId, pageable.getPageNumber(), pageable.getPageSize());

        Page<Room> page;
        if (floorId != null) {
            page = roomRepository.findByFloorId(floorId, pageable);
        } else if (propertyId != null) {
            // Rooms only store floor_id (spec §20.1 — no property_id on rooms), so resolve
            // every floor in this property first, then query rooms across all of them.
            List<UUID> floorIds = floorRepository.findAllByPropertyId(propertyId).stream()
                    .map(Floor::getId)
                    .collect(Collectors.toList());
            page = floorIds.isEmpty()
                    ? new PageImpl<>(List.of(), pageable, 0)
                    : roomRepository.findByFloorIdIn(floorIds, pageable);
        } else {
            page = roomRepository.findAll(pageable);
        }

        return page.map(RoomResponse::from);
    }

    // ── Update ─────────────────────────────────────────────────────────────────

    @Transactional
    public RoomResponse updateRoom(UUID id, UpdateRoomRequest request) {
        log.debug("Updating room — id: {}", id);

        Room room = roomRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Update failed — room not found — id: {}", id);
                    return new ResourceNotFoundException("Room", "id", id);
                });

        if (request.getName() != null && !request.getName().equals(room.getName())) {
            if (roomRepository.existsByFloorIdAndName(room.getFloorId(), request.getName())) {
                log.warn("Update failed — name '{}' already exists on floor {}",
                        request.getName(), room.getFloorId());
                throw new DuplicateResourceException("Room", "floorId+name",
                        room.getFloorId() + "+" + request.getName());
            }
            room.setName(request.getName());
        }

        // saveAndFlush so JPA auditing's @LastModifiedDate lands on the managed entity
        // before the response DTO is built — see PropertyService.updateProperty for why.
        Room updated = roomRepository.saveAndFlush(room);
        log.info("Room updated successfully — id: {}", updated.getId());

        return RoomResponse.from(updated);
    }

    // ── Soft Delete ────────────────────────────────────────────────────────────

    // TODO(Phase 3/4): once meter_room_coverage and room_assignments exist, block deletion
    // while a room has active coverage or an active tenant assignment — same shape as
    // FloorService.deleteFloor()'s active-rooms check. Cannot be enforced yet; those tables
    // don't exist until MeterController/TenancyController are built. Tracked in
    // DEVLOG_STRUCTURE.md open items — don't ship either of those controllers without
    // circling back to this method.
    @Transactional
    public void deleteRoom(UUID id) {
        log.debug("Soft deleting room — id: {}", id);

        Room room = roomRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Delete failed — room not found — id: {}", id);
                    return new ResourceNotFoundException("Room", "id", id);
                });

        if (!room.isActive()) {
            log.warn("Delete skipped — room already inactive — id: {}", id);
            return;
        }

        room.setActive(false);
        roomRepository.saveAndFlush(room);
        log.info("Room soft deleted — id: {}", id);
    }
}
