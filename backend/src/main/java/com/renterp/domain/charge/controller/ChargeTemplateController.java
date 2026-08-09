package com.renterp.domain.charge.controller;

import com.renterp.common.response.ApiResponse;
import com.renterp.common.response.PagedResponse;
import com.renterp.domain.charge.dto.ChargeTemplateResponse;
import com.renterp.domain.charge.dto.CreateChargeTemplateRequest;
import com.renterp.domain.charge.dto.UpdateChargeTemplateRequest;
import com.renterp.domain.charge.entity.ChargeTemplate.DeactivationMode;
import com.renterp.domain.charge.service.ChargeTemplateService;
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
@RequestMapping("/api/v1/charge-templates")
public class ChargeTemplateController {

    private static final Logger log = LogManager.getLogger(ChargeTemplateController.class);

    private final ChargeTemplateService chargeTemplateService;

    public ChargeTemplateController(ChargeTemplateService chargeTemplateService) {
        this.chargeTemplateService = chargeTemplateService;
    }

    // ── POST /api/v1/charge-templates ────────────────────────────────────────────
    @PostMapping
    public ResponseEntity<ApiResponse<ChargeTemplateResponse>> createChargeTemplate(
            @Valid @RequestBody CreateChargeTemplateRequest request) {

        log.debug("POST /api/v1/charge-templates — property: {}, name: {}",
                request.getPropertyId(), request.getName());
        ChargeTemplateResponse response = chargeTemplateService.createChargeTemplate(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Charge template created successfully", response));
    }

    // ── GET /api/v1/charge-templates/{id} ────────────────────────────────────────
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ChargeTemplateResponse>> getChargeTemplateById(@PathVariable UUID id) {
        log.debug("GET /api/v1/charge-templates/{}", id);
        ChargeTemplateResponse response = chargeTemplateService.getChargeTemplateById(id);
        return ResponseEntity.ok(ApiResponse.success("Charge template fetched successfully", response));
    }

    // ── GET /api/v1/charge-templates ─────────────────────────────────────────────
    // Pagination params (all optional — defaults applied below):
    //   ?page=0&size=20&sort=createdAt,desc&propertyId=<uuid>
    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<ChargeTemplateResponse>>> getAllChargeTemplates(
            @RequestParam(required = false) UUID propertyId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {

        log.debug("GET /api/v1/charge-templates — property: {}, page: {}, size: {}",
                propertyId, pageable.getPageNumber(), pageable.getPageSize());
        Page<ChargeTemplateResponse> page = chargeTemplateService.getAllChargeTemplates(propertyId, pageable);
        return ResponseEntity.ok(ApiResponse.success("Charge templates fetched successfully", PagedResponse.from(page)));
    }

    // ── PUT /api/v1/charge-templates/{id} ────────────────────────────────────────
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ChargeTemplateResponse>> updateChargeTemplate(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateChargeTemplateRequest request) {

        log.debug("PUT /api/v1/charge-templates/{}", id);
        ChargeTemplateResponse response = chargeTemplateService.updateChargeTemplate(id, request);
        return ResponseEntity.ok(ApiResponse.success("Charge template updated successfully", response));
    }

    // ── DELETE /api/v1/charge-templates/{id} ─────────────────────────────────────
    // Soft delete (deactivate) only — never removed from the DB. deactivationMode
    // captures the landlord's choice for how the Billing engine should treat this
    // charge going forward (spec §14.2 B7); defaults to NEXT_CYCLE, the safest option
    // since it never retroactively touches a cycle already in progress.
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deactivateChargeTemplate(
            @PathVariable UUID id,
            @RequestParam(required = false, defaultValue = "NEXT_CYCLE") DeactivationMode deactivationMode) {

        log.debug("DELETE /api/v1/charge-templates/{} — mode: {}", id, deactivationMode);
        chargeTemplateService.deactivateChargeTemplate(id, deactivationMode);
        return ResponseEntity.ok(ApiResponse.success("Charge template deactivated successfully"));
    }
}
