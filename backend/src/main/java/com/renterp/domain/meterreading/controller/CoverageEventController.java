package com.renterp.domain.meterreading.controller;

import com.renterp.common.response.ApiResponse;
import com.renterp.common.response.PagedResponse;
import com.renterp.domain.meterreading.dto.CoverageEventResponse;
import com.renterp.domain.meterreading.dto.CreateCoverageEventRequest;
import com.renterp.domain.meterreading.service.CoverageEventService;
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
@RequestMapping("/api/v1/coverage-events")
public class CoverageEventController {

    private static final Logger log = LogManager.getLogger(CoverageEventController.class);

    private final CoverageEventService service;

    public CoverageEventController(CoverageEventService service) {
        this.service = service;
    }

    // Top-level rather than nested — SPLIT/MERGE touches ≥2 meters and doesn't have a
    // single owning meter in its URL.
    @PostMapping
    public ResponseEntity<ApiResponse<CoverageEventResponse>> create(@Valid @RequestBody CreateCoverageEventRequest request) {
        log.debug("POST /coverage-events — type: {}, meters: {}", request.getEventType(), request.getAffectedMeters().size());
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Coverage event created successfully", service.createEvent(request)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CoverageEventResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Coverage event fetched successfully", service.getEvent(id)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<CoverageEventResponse>>> listByProperty(
            @RequestParam UUID propertyId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        Page<CoverageEventResponse> page = service.listByProperty(propertyId, pageable);
        return ResponseEntity.ok(ApiResponse.success("Coverage events fetched successfully", PagedResponse.from(page)));
    }
}
