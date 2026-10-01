package com.renterp.domain.billing.controller;

import com.renterp.domain.auth.security.ResourceAccess;
import com.renterp.common.response.ApiResponse;
import com.renterp.common.response.PagedResponse;
import com.renterp.domain.billing.dto.AdjustmentResponse;
import com.renterp.domain.billing.dto.BillCorrectionResponse;
import com.renterp.domain.billing.dto.CorrectBillRequest;
import com.renterp.domain.billing.dto.CreateAdjustmentRequest;
import com.renterp.domain.billing.dto.TenantBillResponse;
import com.renterp.domain.billing.service.BillAdjustmentService;
import com.renterp.domain.billing.service.BillCorrectionService;
import com.renterp.domain.billing.service.TenantBillService;
import jakarta.validation.Valid;
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
public class TenantBillController {

    private final TenantBillService tenantBillService;
    private final BillAdjustmentService adjustmentService;
    private final BillCorrectionService correctionService;

    private final ResourceAccess access;

    public TenantBillController(ResourceAccess access,
            TenantBillService tenantBillService,
                               BillAdjustmentService adjustmentService,
                               BillCorrectionService correctionService) {
        this.access = access;
        this.tenantBillService = tenantBillService;
        this.adjustmentService = adjustmentService;
        this.correctionService = correctionService;
    }

    @GetMapping("/api/v1/tenant-bills/{id}")
    public ResponseEntity<ApiResponse<TenantBillResponse>> getBill(@PathVariable UUID id) {
        access.tenantBill(id, ResourceAccess.Level.READ);
        return ResponseEntity.ok(ApiResponse.success("Tenant bill fetched", tenantBillService.getById(id)));
    }

    // B8 — correct a confirmed bill. Unpaid → cancel+regenerate; paid/partial → next-bill adjustment.
    @PostMapping("/api/v1/tenant-bills/{id}/correct")
    public ResponseEntity<ApiResponse<BillCorrectionResponse>> correctBill(
            @PathVariable UUID id,
            @Valid @RequestBody CorrectBillRequest req) {
        access.tenantBill(id, ResourceAccess.Level.WRITE);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Bill corrected", correctionService.correct(id, req)));
    }

    @GetMapping("/api/v1/memberships/{mid}/bills")
    public ResponseEntity<ApiResponse<PagedResponse<TenantBillResponse>>> listBills(
            @PathVariable UUID mid,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        access.membership(mid, ResourceAccess.Level.READ);
        Page<TenantBillResponse> page = tenantBillService.listByMembership(mid, pageable);
        return ResponseEntity.ok(ApiResponse.success("Tenant bills fetched", PagedResponse.from(page)));
    }

    // ── One-time carry-forward adjustments (P9) ──────────────────────────────

    @PostMapping("/api/v1/memberships/{mid}/adjustments")
    public ResponseEntity<ApiResponse<AdjustmentResponse>> createAdjustment(
            @PathVariable UUID mid,
            @Valid @RequestBody CreateAdjustmentRequest req) {
        access.membership(mid, ResourceAccess.Level.WRITE);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Adjustment recorded", adjustmentService.create(mid, req)));
    }

    @GetMapping("/api/v1/memberships/{mid}/adjustments")
    public ResponseEntity<ApiResponse<List<AdjustmentResponse>>> listAdjustments(@PathVariable UUID mid) {
        access.membership(mid, ResourceAccess.Level.READ);
        return ResponseEntity.ok(ApiResponse.success("Adjustments fetched",
                adjustmentService.listForMembership(mid)));
    }
}
