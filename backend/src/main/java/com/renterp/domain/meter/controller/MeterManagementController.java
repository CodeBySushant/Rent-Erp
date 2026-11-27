package com.renterp.domain.meter.controller;

import com.renterp.common.response.ApiResponse;
import com.renterp.domain.auth.security.ResourceAccess;
import com.renterp.domain.meter.dto.AddMeterRequest;
import com.renterp.domain.meter.dto.MeterOverviewResponse;
import com.renterp.domain.meter.dto.MeterResponse;
import com.renterp.domain.meter.dto.SetMeterRoomsRequest;
import com.renterp.domain.meter.service.MeterManagementService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Meter Settings on the property page: see, add, re-room and remove electricity
 * meters after setup. Renaming uses {@code PUT /api/v1/meters/{id}}.
 */
@RestController
public class MeterManagementController {

    private final ResourceAccess access;
    private final MeterManagementService service;

    public MeterManagementController(ResourceAccess access, MeterManagementService service) {
        this.access = access;
        this.service = service;
    }

    @GetMapping("/api/v1/properties/{propertyId}/meter-overview")
    public ResponseEntity<ApiResponse<MeterOverviewResponse>> overview(@PathVariable UUID propertyId) {
        access.property(propertyId, ResourceAccess.Level.READ);
        return ResponseEntity.ok(ApiResponse.success("Meters fetched successfully", service.overview(propertyId)));
    }

    @PostMapping("/api/v1/properties/{propertyId}/meters")
    public ResponseEntity<ApiResponse<MeterResponse>> add(@PathVariable UUID propertyId,
                                                          @Valid @RequestBody AddMeterRequest request) {
        access.property(propertyId, ResourceAccess.Level.WRITE);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Meter added", service.addMeter(propertyId, request)));
    }

    @PutMapping("/api/v1/meters/{id}/rooms")
    public ResponseEntity<ApiResponse<List<UUID>>> setRooms(@PathVariable UUID id,
                                                            @Valid @RequestBody SetMeterRoomsRequest request) {
        access.meter(id, ResourceAccess.Level.WRITE);
        return ResponseEntity.ok(ApiResponse.success("Meter rooms saved", service.setRooms(id, request)));
    }

    @PostMapping("/api/v1/meters/{id}/remove")
    public ResponseEntity<ApiResponse<Void>> remove(@PathVariable UUID id) {
        access.meter(id, ResourceAccess.Level.WRITE);
        service.removeMeter(id);
        return ResponseEntity.ok(ApiResponse.success("Meter removed"));
    }
}
