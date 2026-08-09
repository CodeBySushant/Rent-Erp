package com.renterp.domain.propertyaccess.controller;

import com.renterp.common.response.ApiResponse;
import com.renterp.common.response.PagedResponse;
import com.renterp.domain.propertyaccess.dto.CreatePropertyAccessRequest;
import com.renterp.domain.propertyaccess.dto.PropertyAccessResponse;
import com.renterp.domain.propertyaccess.dto.UpdatePropertyAccessRequest;
import com.renterp.domain.propertyaccess.service.PropertyAccessService;
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
@RequestMapping("/api/v1/property-access")
public class PropertyAccessController {

    private static final Logger log = LogManager.getLogger(PropertyAccessController.class);

    private final PropertyAccessService propertyAccessService;

    public PropertyAccessController(PropertyAccessService propertyAccessService) {
        this.propertyAccessService = propertyAccessService;
    }

    // ── POST /api/v1/property-access ───────────────────────────────────────────
    // Grants MANAGER or VIEW_ONLY access. OWNER access is auto-created with the
    // property and rejected here — see PropertyAccessService.createAccess.
    @PostMapping
    public ResponseEntity<ApiResponse<PropertyAccessResponse>> createAccess(
            @Valid @RequestBody CreatePropertyAccessRequest request) {

        log.debug("POST /api/v1/property-access — property: {}, user: {}, role: {}",
                request.getPropertyId(), request.getUserId(), request.getRole());
        PropertyAccessResponse response = propertyAccessService.createAccess(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Property access granted successfully", response));
    }

    // ── GET /api/v1/property-access/{id} ───────────────────────────────────────
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PropertyAccessResponse>> getAccessById(@PathVariable UUID id) {
        log.debug("GET /api/v1/property-access/{}", id);
        PropertyAccessResponse response = propertyAccessService.getAccessById(id);
        return ResponseEntity.ok(ApiResponse.success("Property access fetched successfully", response));
    }

    // ── GET /api/v1/property-access ────────────────────────────────────────────
    // Pagination params (all optional — defaults applied below):
    //   ?page=0              → which page to fetch (0-based)
    //   ?size=20             → records per page
    //   ?sort=createdAt,desc → field to sort by and direction
    //   ?propertyId=<uuid>   → everyone with access to this property (checked before userId)
    //   ?userId=<uuid>       → every property this user has access to (drives the property switcher)
    //
    // Example: GET /api/v1/property-access?propertyId=...&page=0&size=10
    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<PropertyAccessResponse>>> getAllAccess(
            @RequestParam(required = false) UUID propertyId,
            @RequestParam(required = false) UUID userId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {

        log.debug("GET /api/v1/property-access — property: {}, user: {}, page: {}, size: {}",
                propertyId, userId, pageable.getPageNumber(), pageable.getPageSize());
        Page<PropertyAccessResponse> page = propertyAccessService.getAllAccess(propertyId, userId, pageable);
        return ResponseEntity.ok(ApiResponse.success("Property access fetched successfully", PagedResponse.from(page)));
    }

    // ── PUT /api/v1/property-access/{id} ───────────────────────────────────────
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<PropertyAccessResponse>> updateAccess(
            @PathVariable UUID id,
            @Valid @RequestBody UpdatePropertyAccessRequest request) {

        log.debug("PUT /api/v1/property-access/{} — new role: {}", id, request.getRole());
        PropertyAccessResponse response = propertyAccessService.updateAccess(id, request);
        return ResponseEntity.ok(ApiResponse.success("Property access updated successfully", response));
    }

    // ── DELETE /api/v1/property-access/{id} ────────────────────────────────────
    // Soft delete only — sets is_active = false (revokes access). OWNER grants are rejected.
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> revokeAccess(@PathVariable UUID id) {
        log.debug("DELETE /api/v1/property-access/{}", id);
        propertyAccessService.revokeAccess(id);
        return ResponseEntity.ok(ApiResponse.success("Property access revoked successfully"));
    }
}
