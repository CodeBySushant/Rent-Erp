package com.renterp.domain.property.controller;

import com.renterp.common.response.ApiResponse;
import com.renterp.common.response.PagedResponse;
import com.renterp.domain.property.dto.CreatePropertyRequest;
import com.renterp.domain.property.dto.PropertyResponse;
import com.renterp.domain.property.dto.UpdatePropertyRequest;
import com.renterp.domain.property.service.PropertyService;
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
@RequestMapping("/api/v1/properties")
public class PropertyController {

    private static final Logger log = LogManager.getLogger(PropertyController.class);

    private final PropertyService propertyService;

    public PropertyController(PropertyService propertyService) {
        this.propertyService = propertyService;
    }

    // ── POST /api/v1/properties ────────────────────────────────────────────────
    @PostMapping
    public ResponseEntity<ApiResponse<PropertyResponse>> createProperty(
            @Valid @RequestBody CreatePropertyRequest request) {

        log.debug("POST /api/v1/properties — owner: {}, name: {}", request.getOwnerUserId(), request.getName());
        PropertyResponse response = propertyService.createProperty(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Property created successfully", response));
    }

    // ── GET /api/v1/properties/{id} ────────────────────────────────────────────
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PropertyResponse>> getPropertyById(@PathVariable UUID id) {
        log.debug("GET /api/v1/properties/{}", id);
        PropertyResponse response = propertyService.getPropertyById(id);
        return ResponseEntity.ok(ApiResponse.success("Property fetched successfully", response));
    }

    // ── GET /api/v1/properties ─────────────────────────────────────────────────
    // Pagination params (all optional — defaults applied below):
    //   ?page=0             → which page to fetch (0-based, so page=0 is the first page)
    //   ?size=20             → how many records per page
    //   ?sort=createdAt,desc → field to sort by and direction
    //   ?ownerUserId=<uuid>  → filter to properties owned by this user (landlord's own list)
    //
    // Example: GET /api/v1/properties?ownerUserId=...&page=0&size=10
    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<PropertyResponse>>> getAllProperties(
            @RequestParam(required = false) UUID ownerUserId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {

        log.debug("GET /api/v1/properties — owner: {}, page: {}, size: {}", ownerUserId, pageable.getPageNumber(), pageable.getPageSize());
        Page<PropertyResponse> page = propertyService.getAllProperties(ownerUserId, pageable);
        return ResponseEntity.ok(ApiResponse.success("Properties fetched successfully", PagedResponse.from(page)));
    }

    // ── PUT /api/v1/properties/{id} ────────────────────────────────────────────
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<PropertyResponse>> updateProperty(
            @PathVariable UUID id,
            @Valid @RequestBody UpdatePropertyRequest request) {

        log.debug("PUT /api/v1/properties/{}", id);
        PropertyResponse response = propertyService.updateProperty(id, request);
        return ResponseEntity.ok(ApiResponse.success("Property updated successfully", response));
    }

    // ── DELETE /api/v1/properties/{id} ─────────────────────────────────────────
    // Soft delete only — sets is_active = false. Row is never removed from DB.
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteProperty(@PathVariable UUID id) {
        log.debug("DELETE /api/v1/properties/{}", id);
        propertyService.deleteProperty(id);
        return ResponseEntity.ok(ApiResponse.success("Property deactivated successfully"));
    }
}
