package com.renterp.domain.billing.controller;

import com.renterp.domain.auth.security.ResourceAccess;
import com.renterp.common.response.ApiResponse;
import com.renterp.common.response.PagedResponse;
import com.renterp.domain.billing.dto.*;
import com.renterp.domain.billing.service.BillingRunService;
import com.renterp.domain.billing.service.TenantBillService;
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
public class BillingRunController {

    private static final Logger log = LogManager.getLogger(BillingRunController.class);

    private final BillingRunService billingRunService;
    private final TenantBillService tenantBillService;

    private final ResourceAccess access;
    private final com.renterp.domain.payment.service.PaymentGuard paymentGuard;

    public BillingRunController(ResourceAccess access,
            com.renterp.domain.payment.service.PaymentGuard paymentGuard,
            BillingRunService billingRunService, TenantBillService tenantBillService) {
        this.access = access;
        this.paymentGuard = paymentGuard;
        this.billingRunService = billingRunService;
        this.tenantBillService = tenantBillService;
    }

    // Generate a run for a property. Optional Idempotency-Key header OR body field (B10).
    @PostMapping("/api/v1/properties/{propertyId}/billing-runs")
    public ResponseEntity<ApiResponse<BillingRunResponse>> generate(
            @PathVariable UUID propertyId,
            @Valid @RequestBody CreateBillingRunRequest req,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKeyHeader) {
        access.property(propertyId, ResourceAccess.Level.WRITE);

        if (idempotencyKeyHeader != null && req.getIdempotencyKey() == null) {
            req.setIdempotencyKey(idempotencyKeyHeader);
        }
        log.debug("POST billing-run — property: {}, period: {}", propertyId, req.getBillingMonthBs());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Billing run generated", billingRunService.generate(propertyId, req)));
    }

    @GetMapping("/api/v1/billing-runs/{id}")
    public ResponseEntity<ApiResponse<BillingRunResponse>> getRun(@PathVariable UUID id) {
        access.billingRun(id, ResourceAccess.Level.READ);
        return ResponseEntity.ok(ApiResponse.success("Billing run fetched", billingRunService.getRun(id)));
    }

    @GetMapping("/api/v1/properties/{propertyId}/billing-runs")
    public ResponseEntity<ApiResponse<PagedResponse<BillingRunResponse>>> listRuns(
            @PathVariable UUID propertyId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        access.property(propertyId, ResourceAccess.Level.READ);
        Page<BillingRunResponse> page = billingRunService.listRuns(propertyId, pageable);
        return ResponseEntity.ok(ApiResponse.success("Billing runs fetched", PagedResponse.from(page)));
    }

    @GetMapping("/api/v1/billing-runs/{id}/segments")
    public ResponseEntity<ApiResponse<List<BillingRunSegmentResponse>>> getSegments(@PathVariable UUID id) {
        access.billingRun(id, ResourceAccess.Level.READ);
        return ResponseEntity.ok(ApiResponse.success("Segments fetched", billingRunService.getSegments(id)));
    }

    @GetMapping("/api/v1/billing-runs/{id}/bills")
    public ResponseEntity<ApiResponse<List<TenantBillResponse>>> getBills(@PathVariable UUID id) {
        access.billingRun(id, ResourceAccess.Level.READ);
        return ResponseEntity.ok(ApiResponse.success("Bills fetched", tenantBillService.listByRun(id)));
    }

    // B15 — poll async run progress (RUNNING → COMPLETED | FAILED).
    @GetMapping("/api/v1/billing-runs/{id}/progress")
    public ResponseEntity<ApiResponse<BillingRunProgressResponse>> getProgress(@PathVariable UUID id) {
        access.billingRun(id, ResourceAccess.Level.READ);
        return ResponseEntity.ok(ApiResponse.success("Progress fetched", billingRunService.getProgress(id)));
    }

    @PostMapping("/api/v1/billing-runs/{id}/confirm")
    public ResponseEntity<ApiResponse<BillingRunResponse>> confirm(
            @PathVariable UUID id,
            @RequestBody(required = false) ConfirmBillingRunRequest req) {
        access.billingRun(id, ResourceAccess.Level.WRITE);
        return ResponseEntity.ok(ApiResponse.success("Billing run confirmed", billingRunService.confirm(id, req)));
    }

    @PostMapping("/api/v1/billing-runs/{id}/cancel")
    public ResponseEntity<ApiResponse<BillingRunResponse>> cancel(
            @PathVariable UUID id,
            @Valid @RequestBody CancelBillingRunRequest req) {
        access.billingRun(id, ResourceAccess.Level.WRITE);
        paymentGuard.requireNoPaymentsOnRun(id);
        return ResponseEntity.ok(ApiResponse.success("Billing run cancelled", billingRunService.cancel(id, req)));
    }
}
