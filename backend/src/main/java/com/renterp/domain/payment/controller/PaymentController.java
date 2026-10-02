package com.renterp.domain.payment.controller;

import com.renterp.common.response.ApiResponse;
import com.renterp.common.response.PagedResponse;
import com.renterp.domain.auth.security.ResourceAccess;
import com.renterp.domain.payment.dto.PaymentResponse;
import com.renterp.domain.payment.dto.RecordPaymentRequest;
import com.renterp.domain.payment.dto.RejectPaymentRequest;
import com.renterp.domain.payment.entity.Payment.Status;
import com.renterp.domain.payment.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Payments against tenant bills: owner records, tenant proofs, approvals. */
@RestController
public class PaymentController {

    private final PaymentService service;
    private final ResourceAccess access;

    public PaymentController(PaymentService service, ResourceAccess access) {
        this.service = service;
        this.access = access;
    }

    // ── Owner records money received ────────────────────────────────────────────
    @PostMapping("/api/v1/tenant-bills/{billId}/payments")
    public ResponseEntity<ApiResponse<PaymentResponse>> record(
            @PathVariable UUID billId, @Valid @RequestBody RecordPaymentRequest req,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        access.tenantBill(billId, ResourceAccess.Level.WRITE);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Payment recorded", service.record(billId, req, key)));
    }

    // ── Tenant sends proof ──────────────────────────────────────────────────────
    @PostMapping("/api/v1/tenant-bills/{billId}/payment-proofs")
    public ResponseEntity<ApiResponse<PaymentResponse>> submitProof(
            @PathVariable UUID billId, @Valid @RequestBody RecordPaymentRequest req,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        access.billOwnTenant(billId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Payment proof sent", service.submitProof(billId, req, key)));
    }

    @GetMapping("/api/v1/tenant-bills/{billId}/payments")
    public ResponseEntity<ApiResponse<List<PaymentResponse>>> forBill(@PathVariable UUID billId) {
        access.tenantBill(billId, ResourceAccess.Level.READ);
        return ResponseEntity.ok(ApiResponse.success("Payments fetched", service.forBill(billId)));
    }

    // ── Owner decides on a proof ────────────────────────────────────────────────
    @PostMapping("/api/v1/payments/{id}/approve")
    public ResponseEntity<ApiResponse<PaymentResponse>> approve(@PathVariable UUID id) {
        access.payment(id, ResourceAccess.Level.WRITE);
        return ResponseEntity.ok(ApiResponse.success("Payment approved", service.approve(id)));
    }

    @PostMapping("/api/v1/payments/{id}/reject")
    public ResponseEntity<ApiResponse<PaymentResponse>> reject(@PathVariable UUID id,
                                                               @Valid @RequestBody RejectPaymentRequest req) {
        access.payment(id, ResourceAccess.Level.WRITE);
        return ResponseEntity.ok(ApiResponse.success("Payment rejected", service.reject(id, req.getReason())));
    }

    // ── Tenant withdraws a pending proof ───────────────────────────────────────
    @PostMapping("/api/v1/payments/{id}/cancel")
    public ResponseEntity<ApiResponse<PaymentResponse>> cancel(@PathVariable UUID id) {
        access.paymentOwnTenant(id);
        return ResponseEntity.ok(ApiResponse.success("Payment withdrawn", service.cancel(id)));
    }

    // ── Lists ───────────────────────────────────────────────────────────────────
    @GetMapping("/api/v1/properties/{propertyId}/payments")
    public ResponseEntity<ApiResponse<PagedResponse<PaymentResponse>>> forProperty(
            @PathVariable UUID propertyId, @RequestParam(required = false) Status status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        access.property(propertyId, ResourceAccess.Level.READ);
        return ResponseEntity.ok(ApiResponse.success("Payments fetched",
                PagedResponse.from(service.forProperty(propertyId, status, pageable))));
    }

    @GetMapping("/api/v1/me/payments")
    public ResponseEntity<ApiResponse<PagedResponse<PaymentResponse>>> mine(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Payments fetched",
                PagedResponse.from(service.mine(pageable))));
    }
}
