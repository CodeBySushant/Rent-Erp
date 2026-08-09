package com.renterp.domain.meter.controller;

import com.renterp.common.response.ApiResponse;
import com.renterp.common.response.PagedResponse;
import com.renterp.domain.meter.dto.*;
import com.renterp.domain.meter.entity.Meter.MeterPurpose;
import com.renterp.domain.meter.entity.Meter.MeterType;
import com.renterp.domain.meter.service.MeterService;
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

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/meters")
public class MeterController {

    private static final Logger log = LogManager.getLogger(MeterController.class);

    private final MeterService meterService;

    public MeterController(MeterService meterService) {
        this.meterService = meterService;
    }

    // ── POST /api/v1/meters ─────────────────────────────────────────────────────
    @PostMapping
    public ResponseEntity<ApiResponse<MeterResponse>> createMeter(
            @Valid @RequestBody CreateMeterRequest request) {

        log.debug("POST /api/v1/meters — property: {}, label: {}, type: {}",
                request.getPropertyId(), request.getLabel(), request.getMeterType());
        MeterResponse response = meterService.createMeter(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Meter created successfully", response));
    }

    // ── GET /api/v1/meters/{id} ─────────────────────────────────────────────────
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<MeterResponse>> getMeterById(@PathVariable UUID id) {
        log.debug("GET /api/v1/meters/{}", id);
        return ResponseEntity.ok(ApiResponse.success("Meter fetched successfully",
                meterService.getMeterById(id)));
    }

    // ── GET /api/v1/meters ──────────────────────────────────────────────────────
    // Optional filters: ?propertyId=<uuid>&meterType=<TYPE>&meterPurpose=<PURPOSE>
    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<MeterResponse>>> getAllMeters(
            @RequestParam(required = false) UUID propertyId,
            @RequestParam(required = false) MeterType meterType,
            @RequestParam(required = false) MeterPurpose meterPurpose,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {

        log.debug("GET /api/v1/meters — property: {}, type: {}, purpose: {}",
                propertyId, meterType, meterPurpose);
        Page<MeterResponse> page = meterService.getAllMeters(propertyId, meterType, meterPurpose, pageable);
        return ResponseEntity.ok(ApiResponse.success("Meters fetched successfully", PagedResponse.from(page)));
    }

    // ── PUT /api/v1/meters/{id} ─────────────────────────────────────────────────
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<MeterResponse>> updateMeter(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateMeterRequest request) {

        log.debug("PUT /api/v1/meters/{}", id);
        return ResponseEntity.ok(ApiResponse.success("Meter updated successfully",
                meterService.updateMeter(id, request)));
    }

    // ── DELETE /api/v1/meters/{id} ──────────────────────────────────────────────
    // Soft-deactivates the meter (§7.8 / §14.1 M11). Blocked while active coverage rows exist —
    // end coverage first. Never removes the record; meters remain readable for historical bills.
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deactivateMeter(@PathVariable UUID id) {
        log.debug("DELETE /api/v1/meters/{}", id);
        meterService.deactivateMeter(id);
        return ResponseEntity.ok(ApiResponse.success("Meter deactivated successfully"));
    }

    // ── Coverage sub-resource ───────────────────────────────────────────────────

    // POST /api/v1/meters/{id}/coverage — open a dated coverage row (meter↔room, structural).
    @PostMapping("/{id}/coverage")
    public ResponseEntity<ApiResponse<MeterRoomCoverageResponse>> addRoomCoverage(
            @PathVariable UUID id,
            @Valid @RequestBody CreateMeterRoomCoverageRequest request) {

        log.debug("POST /api/v1/meters/{}/coverage — room: {}", id, request.getRoomId());
        MeterRoomCoverageResponse response = meterService.addRoomCoverage(id, request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Coverage row created successfully", response));
    }

    // GET /api/v1/meters/{id}/coverage — full dated coverage history for this meter.
    @GetMapping("/{id}/coverage")
    public ResponseEntity<ApiResponse<List<MeterRoomCoverageResponse>>> listRoomCoverage(@PathVariable UUID id) {
        log.debug("GET /api/v1/meters/{}/coverage", id);
        return ResponseEntity.ok(ApiResponse.success("Coverage rows fetched successfully",
                meterService.listRoomCoverage(id)));
    }

    // PATCH /api/v1/meters/{id}/coverage/{coverageId}/end — close an active coverage row.
    // PATCH instead of DELETE because the row is not removed — only its effectiveToBs is set.
    @PatchMapping("/{id}/coverage/{coverageId}/end")
    public ResponseEntity<ApiResponse<MeterRoomCoverageResponse>> endRoomCoverage(
            @PathVariable UUID id,
            @PathVariable UUID coverageId,
            @Valid @RequestBody EndMeterRoomCoverageRequest request) {

        log.debug("PATCH /api/v1/meters/{}/coverage/{}/end — to: {}",
                id, coverageId, request.getEffectiveToBs());
        return ResponseEntity.ok(ApiResponse.success("Coverage row ended successfully",
                meterService.endRoomCoverage(id, coverageId, request)));
    }

    // ── Infrastructure scope sub-resource ───────────────────────────────────────

    // POST /api/v1/meters/{id}/infra-scope — add a floor (or tenant, later) to an infra meter's scope.
    @PostMapping("/{id}/infra-scope")
    public ResponseEntity<ApiResponse<InfrastructureMeterScopeResponse>> addInfrastructureScope(
            @PathVariable UUID id,
            @Valid @RequestBody CreateInfrastructureMeterScopeRequest request) {

        log.debug("POST /api/v1/meters/{}/infra-scope — scopeBy: {}", id, request.getScopeBy());
        InfrastructureMeterScopeResponse response = meterService.addInfrastructureScope(id, request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Infrastructure scope row created successfully", response));
    }

    @GetMapping("/{id}/infra-scope")
    public ResponseEntity<ApiResponse<List<InfrastructureMeterScopeResponse>>> listInfrastructureScope(
            @PathVariable UUID id) {

        log.debug("GET /api/v1/meters/{}/infra-scope", id);
        return ResponseEntity.ok(ApiResponse.success("Infrastructure scope rows fetched successfully",
                meterService.listInfrastructureScope(id)));
    }

    // DELETE — the scope row is a live set membership, not a historical ledger, so a
    // hard delete is correct here (see MeterService.deleteInfrastructureScope for the rationale).
    @DeleteMapping("/{id}/infra-scope/{scopeId}")
    public ResponseEntity<ApiResponse<Void>> deleteInfrastructureScope(
            @PathVariable UUID id,
            @PathVariable UUID scopeId) {

        log.debug("DELETE /api/v1/meters/{}/infra-scope/{}", id, scopeId);
        meterService.deleteInfrastructureScope(id, scopeId);
        return ResponseEntity.ok(ApiResponse.success("Infrastructure scope row deleted successfully"));
    }
}
