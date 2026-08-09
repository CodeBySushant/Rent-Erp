package com.renterp.domain.structure.service;

import com.renterp.common.exception.DuplicateResourceException;
import com.renterp.common.exception.InvalidOperationException;
import com.renterp.common.exception.ResourceNotFoundException;
import com.renterp.domain.property.repository.PropertyRepository;
import com.renterp.domain.structure.dto.CreateFloorRequest;
import com.renterp.domain.structure.dto.FloorResponse;
import com.renterp.domain.structure.dto.UpdateFloorRequest;
import com.renterp.domain.structure.entity.Floor;
import com.renterp.domain.structure.repository.FloorRepository;
import com.renterp.domain.structure.repository.RoomRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class FloorService {

    private static final Logger log = LogManager.getLogger(FloorService.class);

    private final FloorRepository floorRepository;
    private final RoomRepository roomRepository;
    private final PropertyRepository propertyRepository;

    public FloorService(FloorRepository floorRepository, RoomRepository roomRepository,
                         PropertyRepository propertyRepository) {
        this.floorRepository = floorRepository;
        this.roomRepository = roomRepository;
        this.propertyRepository = propertyRepository;
    }

    // ── Create ─────────────────────────────────────────────────────────────────

    @Transactional
    public FloorResponse createFloor(CreateFloorRequest request) {
        log.debug("Creating floor — property: {}, name: {}, floorNumber: {}",
                request.getPropertyId(), request.getName(), request.getFloorNumber());

        if (!propertyRepository.existsById(request.getPropertyId())) {
            log.warn("Floor creation failed — property not found: {}", request.getPropertyId());
            throw new ResourceNotFoundException("Property", "id", request.getPropertyId());
        }
        if (floorRepository.existsByPropertyIdAndFloorNumber(request.getPropertyId(), request.getFloorNumber())) {
            log.warn("Floor creation failed — floorNumber {} already exists for property {}",
                    request.getFloorNumber(), request.getPropertyId());
            throw new DuplicateResourceException("Floor", "propertyId+floorNumber",
                    request.getPropertyId() + "+" + request.getFloorNumber());
        }

        Floor floor = Floor.builder()
                .propertyId(request.getPropertyId())
                .name(request.getName())
                .floorNumber(request.getFloorNumber())
                .build();

        Floor saved = floorRepository.save(floor);
        log.info("Floor created successfully — id: {}, property: {}, floorNumber: {}",
                saved.getId(), saved.getPropertyId(), saved.getFloorNumber());

        return FloorResponse.from(saved);
    }

    // ── Read single ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public FloorResponse getFloorById(UUID id) {
        log.debug("Fetching floor by id: {}", id);

        Floor floor = floorRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Floor not found — id: {}", id);
                    return new ResourceNotFoundException("Floor", "id", id);
                });

        return FloorResponse.from(floor);
    }

    // ── Read all (paginated, optionally filtered by property) ──────────────────

    @Transactional(readOnly = true)
    public Page<FloorResponse> getAllFloors(UUID propertyId, Pageable pageable) {
        log.debug("Fetching floors — property: {}, page: {}, size: {}",
                propertyId, pageable.getPageNumber(), pageable.getPageSize());

        Page<Floor> page = (propertyId != null)
                ? floorRepository.findByPropertyId(propertyId, pageable)
                : floorRepository.findAll(pageable);

        return page.map(FloorResponse::from);
    }

    // ── Update ─────────────────────────────────────────────────────────────────

    @Transactional
    public FloorResponse updateFloor(UUID id, UpdateFloorRequest request) {
        log.debug("Updating floor — id: {}", id);

        Floor floor = floorRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Update failed — floor not found — id: {}", id);
                    return new ResourceNotFoundException("Floor", "id", id);
                });

        if (request.getName() != null) {
            floor.setName(request.getName());
        }
        if (request.getFloorNumber() != null && request.getFloorNumber() != floor.getFloorNumber()) {
            if (floorRepository.existsByPropertyIdAndFloorNumber(floor.getPropertyId(), request.getFloorNumber())) {
                log.warn("Update failed — floorNumber {} already exists for property {}",
                        request.getFloorNumber(), floor.getPropertyId());
                throw new DuplicateResourceException("Floor", "propertyId+floorNumber",
                        floor.getPropertyId() + "+" + request.getFloorNumber());
            }
            floor.setFloorNumber(request.getFloorNumber());
        }

        // saveAndFlush so JPA auditing's @LastModifiedDate lands on the managed entity
        // before the response DTO is built — see PropertyService.updateProperty for why.
        Floor updated = floorRepository.saveAndFlush(floor);
        log.info("Floor updated successfully — id: {}", updated.getId());

        return FloorResponse.from(updated);
    }

    // ── Soft Delete ────────────────────────────────────────────────────────────

    @Transactional
    public void deleteFloor(UUID id) {
        log.debug("Soft deleting floor — id: {}", id);

        Floor floor = floorRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Delete failed — floor not found — id: {}", id);
                    return new ResourceNotFoundException("Floor", "id", id);
                });

        if (roomRepository.existsByFloorIdAndActive(id, true)) {
            log.warn("Delete blocked — floor {} still has active rooms", id);
            throw new InvalidOperationException(
                    "Floor cannot be deleted while it still has active rooms — deactivate its rooms first");
        }

        if (!floor.isActive()) {
            log.warn("Delete skipped — floor already inactive — id: {}", id);
            return;
        }

        floor.setActive(false);
        floorRepository.saveAndFlush(floor);
        log.info("Floor soft deleted — id: {}", id);
    }
}
