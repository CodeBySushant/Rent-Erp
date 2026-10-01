package com.renterp.domain.meterreading.controller;

import com.renterp.domain.auth.security.ResourceAccess;
import com.renterp.common.response.ApiResponse;
import com.renterp.common.response.PagedResponse;
import com.renterp.domain.meterreading.dto.*;
import com.renterp.domain.meterreading.entity.MeterReading.ReadingStatus;
import com.renterp.domain.meterreading.entity.MeterReading.ReadingType;
import com.renterp.domain.meterreading.service.MeterReadingService;
import com.renterp.domain.meterreading.service.MeterReplacementService;
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
public class MeterReadingController {

    private static final Logger log = LogManager.getLogger(MeterReadingController.class);

    private final MeterReadingService readingService;
    private final MeterReplacementService replacementService;

    private final ResourceAccess access;

    public MeterReadingController(ResourceAccess access,
            MeterReadingService readingService,
                                   MeterReplacementService replacementService) {
        this.access = access;
        this.readingService = readingService;
        this.replacementService = replacementService;
    }

    // ── Reading log — nested under /meters/{meterId} ────────────────────────────

    @PostMapping("/api/v1/meters/{meterId}/readings")
    public ResponseEntity<ApiResponse<MeterReadingResponse>> submit(
            @PathVariable UUID meterId, @Valid @RequestBody SubmitReadingRequest request) {
        access.meter(meterId, ResourceAccess.Level.WRITE);
        log.debug("POST /meters/{}/readings — type: {}, date: {}", meterId, request.getReadingType(), request.getReadingDateBs());
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Reading submitted (PENDING) — call /confirm to seal it", readingService.submitReading(meterId, request)));
    }

    @GetMapping("/api/v1/meters/{meterId}/readings")
    public ResponseEntity<ApiResponse<PagedResponse<MeterReadingResponse>>> listForMeter(
            @PathVariable UUID meterId,
            @RequestParam(required = false) ReadingType type,
            @RequestParam(required = false) ReadingStatus status,
            @PageableDefault(size = 20, sort = "readingDateBs", direction = Sort.Direction.DESC)
            Pageable pageable) {
        access.meter(meterId, ResourceAccess.Level.READ);
        log.debug("GET /meters/{}/readings — type: {}, status: {}", meterId, type, status);
        Page<MeterReadingResponse> page = readingService.getReadingsForMeter(meterId, type, status, pageable);
        return ResponseEntity.ok(ApiResponse.success("Readings fetched successfully", PagedResponse.from(page)));
    }

    // ── Single reading — top-level so links from correction/event rows resolve ──

    @GetMapping("/api/v1/readings/{id}")
    public ResponseEntity<ApiResponse<MeterReadingResponse>> get(@PathVariable UUID id) {
        access.reading(id, ResourceAccess.Level.READ);
        return ResponseEntity.ok(ApiResponse.success("Reading fetched successfully", readingService.getReadingById(id)));
    }

    // Confirm — PENDING → CONFIRMED. Once confirmed, the row is immutable (§7.5).
    @PostMapping("/api/v1/readings/{id}/confirm")
    public ResponseEntity<ApiResponse<MeterReadingResponse>> confirm(@PathVariable UUID id) {
        access.reading(id, ResourceAccess.Level.WRITE);
        return ResponseEntity.ok(ApiResponse.success("Reading confirmed", readingService.confirmReading(id)));
    }

    // Discard — only while PENDING; a draft the landlord decided not to submit.
    @DeleteMapping("/api/v1/readings/{id}")
    public ResponseEntity<ApiResponse<Void>> discard(@PathVariable UUID id) {
        access.reading(id, ResourceAccess.Level.WRITE);
        readingService.discardPendingReading(id);
        return ResponseEntity.ok(ApiResponse.success("PENDING reading discarded"));
    }

    // Correction — append a CORRECTION row against a CONFIRMED reading. Auto-confirmed
    // because a correction is a fix, not a draft (spec §9.6).
    @PostMapping("/api/v1/readings/{id}/corrections")
    public ResponseEntity<ApiResponse<MeterReadingResponse>> correct(
            @PathVariable UUID id, @Valid @RequestBody CorrectionRequest request) {
        access.reading(id, ResourceAccess.Level.WRITE);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Correction recorded", readingService.correctReading(id, request)));
    }

    // ── §14.1 M5 estimation helper (read-only) ──────────────────────────────────

    @GetMapping("/api/v1/meters/{meterId}/estimation-hint")
    public ResponseEntity<ApiResponse<EstimationHintResponse>> estimationHint(@PathVariable UUID meterId) {
        access.meter(meterId, ResourceAccess.Level.READ);
        return ResponseEntity.ok(ApiResponse.success("Estimation hint computed", readingService.estimationHint(meterId)));
    }

    // ── Replacement (§14.1 M4) — atomic close-old + new-meter + open ────────────

    @PostMapping("/api/v1/meters/{meterId}/replacement")
    public ResponseEntity<ApiResponse<ReplacementEventResponse>> replace(
            @PathVariable UUID meterId, @Valid @RequestBody CreateReplacementRequest request) {
        access.meter(meterId, ResourceAccess.Level.WRITE);
        log.debug("POST /meters/{}/replacement — date: {}", meterId, request.getReplacementDateBs());
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Meter replaced successfully", replacementService.replace(meterId, request)));
    }

    @GetMapping("/api/v1/meters/{meterId}/replacement-events")
    public ResponseEntity<ApiResponse<List<ReplacementEventResponse>>> listReplacements(@PathVariable UUID meterId) {
        access.meter(meterId, ResourceAccess.Level.READ);
        return ResponseEntity.ok(ApiResponse.success("Replacement events fetched", replacementService.listForMeter(meterId)));
    }
}
