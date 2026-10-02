package com.renterp.domain.meterreading.controller;

import com.renterp.common.response.ApiResponse;
import com.renterp.domain.auth.security.ResourceAccess;
import com.renterp.domain.meterreading.dto.MeterReadingResponse;
import com.renterp.domain.meterreading.dto.ReadingDueResponse;
import com.renterp.domain.meterreading.dto.TenantReadingRequest;
import com.renterp.domain.meterreading.service.ReadingDueService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** This month's readings: what is due (owner), my meters and my submission (tenant). */
@RestController
public class ReadingDueController {

    private final ReadingDueService service;
    private final ResourceAccess access;

    public ReadingDueController(ReadingDueService service, ResourceAccess access) {
        this.service = service;
        this.access = access;
    }

    // ── GET /api/v1/properties/{propertyId}/readings/due ─────────────────────────
    @GetMapping("/api/v1/properties/{propertyId}/readings/due")
    public ResponseEntity<ApiResponse<List<ReadingDueResponse>>> due(@PathVariable UUID propertyId) {
        access.property(propertyId, ResourceAccess.Level.READ);
        return ResponseEntity.ok(ApiResponse.success("Readings due fetched", service.dueForProperty(propertyId)));
    }

    // ── GET /api/v1/me/meters ────────────────────────────────────────────────────
    @GetMapping("/api/v1/me/meters")
    public ResponseEntity<ApiResponse<List<ReadingDueResponse>>> myMeters() {
        return ResponseEntity.ok(ApiResponse.success("Meters fetched", service.myMeters()));
    }

    // ── POST /api/v1/me/meters/{meterId}/readings ──────────────────────────────────
    @PostMapping("/api/v1/me/meters/{meterId}/readings")
    public ResponseEntity<ApiResponse<MeterReadingResponse>> submit(@PathVariable UUID meterId,
                                                                    @Valid @RequestBody TenantReadingRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Reading submitted", service.submitOwn(meterId, req)));
    }
}
