package com.renterp.domain.structure.controller;

import com.renterp.common.response.ApiResponse;
import com.renterp.common.response.PagedResponse;
import com.renterp.domain.structure.dto.CreateFloorRequest;
import com.renterp.domain.structure.dto.FloorResponse;
import com.renterp.domain.structure.dto.UpdateFloorRequest;
import com.renterp.domain.structure.service.FloorService;
import jakarta.validation.Valid;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/floors")
public class FloorController {

    private static final Logger log = LogManager.getLogger(FloorController.class);

    private final FloorService floorService;

    public FloorController(FloorService floorService) {
        this.floorService = floorService;
    }

    // ── POST /api/v1/floors ─────────────────────────────────────────────────────
    @PostMapping
    public ResponseEntity<ApiResponse<FloorResponse>> createFloor(
            @Valid @RequestBody CreateFloorRequest request) {

        log.debug("POST /api/v1/floors — property: {}, name: {}", request.getPropertyId(), request.getName());
        FloorResponse response = floorService.createFloor(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Floor created successfully", response));
    }

    // ── GET /api/v1/floors/{id} ──────────────────────────────────────────────────
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<FloorResponse>> getFloorById(@PathVariable UUID id) {
        log.debug("GET /api/v1/floors/{}", id);
        FloorResponse response = floorService.getFloorById(id);
        return ResponseEntity.ok(ApiResponse.success("Floor fetched successfully", response));
    }

    // ── GET /api/v1/floors ───────────────────────────────────────────────────────
    // Pagination params (all optional — defaults applied below):
    //   ?page=0                        → which page to fetch (0-based)
    //   ?size=20                       → records per page
    //   ?sort=floorNumber,asc          → field to sort by and direction
    //   ?propertyId=<uuid>             → floors belonging to this property
    //
    // Default sort is floorNumber ascending, not createdAt — floors are read in their
    // physical top-to-bottom order, not creation order.
    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<FloorResponse>>> getAllFloors(
            @RequestParam(required = false) UUID propertyId,
            @PageableDefault(size = 20, sort = "floorNumber", direction = Sort.Direction.ASC)
            Pageable pageable) {

        log.debug("GET /api/v1/floors — property: {}, page: {}, size: {}",
                propertyId, pageable.getPageNumber(), pageable.getPageSize());
        Page<FloorResponse> page = floorService.getAllFloors(propertyId, pageable);
        return ResponseEntity.ok(ApiResponse.success("Floors fetched successfully", PagedResponse.from(page)));
    }

    // ── PUT /api/v1/floors/{id} ──────────────────────────────────────────────────
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<FloorResponse>> updateFloor(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateFloorRequest request) {

        log.debug("PUT /api/v1/floors/{}", id);
        FloorResponse response = floorService.updateFloor(id, request);
        return ResponseEntity.ok(ApiResponse.success("Floor updated successfully", response));
    }

    // ── DELETE /api/v1/floors/{id} ───────────────────────────────────────────────
    // Soft delete only — sets is_active = false. Blocked if the floor still has active rooms.
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteFloor(@PathVariable UUID id) {
        log.debug("DELETE /api/v1/floors/{}", id);
        floorService.deleteFloor(id);
        return ResponseEntity.ok(ApiResponse.success("Floor deactivated successfully"));
    }
}
