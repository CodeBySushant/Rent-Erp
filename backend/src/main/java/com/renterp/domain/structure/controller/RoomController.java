package com.renterp.domain.structure.controller;

import com.renterp.domain.auth.security.ResourceAccess;
import com.renterp.common.response.ApiResponse;
import com.renterp.common.response.PagedResponse;
import com.renterp.domain.structure.dto.CreateRoomRequest;
import com.renterp.domain.structure.dto.RoomResponse;
import com.renterp.domain.structure.dto.UpdateRoomRequest;
import com.renterp.domain.structure.service.RoomService;
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
@RequestMapping("/api/v1/rooms")
public class RoomController {

    private static final Logger log = LogManager.getLogger(RoomController.class);

    private final RoomService roomService;

    private final ResourceAccess access;

    public RoomController(ResourceAccess access,
            RoomService roomService) {
        this.access = access;
        this.roomService = roomService;
    }

    // ── POST /api/v1/rooms ───────────────────────────────────────────────────────
    @PostMapping
    public ResponseEntity<ApiResponse<RoomResponse>> createRoom(
            @Valid @RequestBody CreateRoomRequest request) {
        access.floor(request.getFloorId(), ResourceAccess.Level.WRITE);

        log.debug("POST /api/v1/rooms — floor: {}, name: {}", request.getFloorId(), request.getName());
        RoomResponse response = roomService.createRoom(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Room created successfully", response));
    }

    // ── GET /api/v1/rooms/{id} ───────────────────────────────────────────────────
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<RoomResponse>> getRoomById(@PathVariable UUID id) {
        access.room(id, ResourceAccess.Level.READ);
        log.debug("GET /api/v1/rooms/{}", id);
        RoomResponse response = roomService.getRoomById(id);
        return ResponseEntity.ok(ApiResponse.success("Room fetched successfully", response));
    }

    // ── GET /api/v1/rooms ─────────────────────────────────────────────────────────
    // Pagination params (all optional — defaults applied below):
    //   ?page=0&size=20&sort=createdAt,desc
    //   ?floorId=<uuid>       → rooms on this floor (checked before propertyId)
    //   ?propertyId=<uuid>    → every room across the whole property (resolved via its floors —
    //                           needed for the "pick vacant rooms" multi-select, spec §10.2)
    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<RoomResponse>>> getAllRooms(
            @RequestParam(required = false) UUID floorId,
            @RequestParam(required = false) UUID propertyId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        if (floorId != null) {
            access.floor(floorId, ResourceAccess.Level.READ);
        } else {
            access.propertyList(propertyId);
        }

        log.debug("GET /api/v1/rooms — floor: {}, property: {}, page: {}, size: {}",
                floorId, propertyId, pageable.getPageNumber(), pageable.getPageSize());
        Page<RoomResponse> page = roomService.getAllRooms(floorId, propertyId, pageable);
        return ResponseEntity.ok(ApiResponse.success("Rooms fetched successfully", PagedResponse.from(page)));
    }

    // ── PUT /api/v1/rooms/{id} ───────────────────────────────────────────────────
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<RoomResponse>> updateRoom(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateRoomRequest request) {
        access.room(id, ResourceAccess.Level.WRITE);

        log.debug("PUT /api/v1/rooms/{}", id);
        RoomResponse response = roomService.updateRoom(id, request);
        return ResponseEntity.ok(ApiResponse.success("Room updated successfully", response));
    }

    // ── DELETE /api/v1/rooms/{id} ────────────────────────────────────────────────
    // Soft delete only — sets is_active = false. No active-reference check yet (see
    // RoomService.deleteRoom TODO) — meter/tenant coupling tables don't exist until
    // later phases.
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteRoom(@PathVariable UUID id) {
        access.room(id, ResourceAccess.Level.WRITE);
        log.debug("DELETE /api/v1/rooms/{}", id);
        roomService.deleteRoom(id);
        return ResponseEntity.ok(ApiResponse.success("Room deactivated successfully"));
    }
}
