package com.renterp.domain.tenantfinance.controller;

import com.renterp.common.response.ApiResponse;
import com.renterp.domain.tenantfinance.dto.*;
import com.renterp.domain.tenantfinance.service.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

// Everything TenantFinance touches is namespaced under a membership — deposits, advance
// rent, opening balance, and rent increments all belong to a specific tenancy. Top-level
// GET /{id} endpoints for advance-rent and rent-increments are added for cross-membership
// lookups (e.g. from billing).
@RestController
public class TenantFinanceController {

    private final TenantDepositService depositService;
    private final TenantAdvanceRentService advanceService;
    private final TenantOpeningBalanceService openingBalanceService;
    private final RentIncrementService rentIncrementService;

    public TenantFinanceController(TenantDepositService depositService,
                                    TenantAdvanceRentService advanceService,
                                    TenantOpeningBalanceService openingBalanceService,
                                    RentIncrementService rentIncrementService) {
        this.depositService = depositService;
        this.advanceService = advanceService;
        this.openingBalanceService = openingBalanceService;
        this.rentIncrementService = rentIncrementService;
    }

    // ── Deposit — one per membership ─────────────────────────

    @PostMapping("/api/v1/memberships/{mid}/deposit")
    public ResponseEntity<ApiResponse<DepositResponse>> createDeposit(@PathVariable UUID mid,
                                                                       @Valid @RequestBody CreateDepositRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Deposit recorded", depositService.create(mid, req)));
    }

    @GetMapping("/api/v1/memberships/{mid}/deposit")
    public ResponseEntity<ApiResponse<DepositResponse>> getDeposit(@PathVariable UUID mid) {
        return ResponseEntity.ok(ApiResponse.success("Deposit fetched", depositService.get(mid)));
    }

    // Correction path only — amount/receivedAtBs/notes. Status transitions (REFUNDED_*,
    // FORFEITED, APPLIED_TO_BALANCE) are Vacancy's concern (Phase 7).
    @PutMapping("/api/v1/memberships/{mid}/deposit")
    public ResponseEntity<ApiResponse<DepositResponse>> updateDeposit(@PathVariable UUID mid,
                                                                       @Valid @RequestBody UpdateDepositRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Deposit updated", depositService.update(mid, req)));
    }

    // ── Advance rent — multiple per membership ────────────────

    @PostMapping("/api/v1/memberships/{mid}/advance-rent")
    public ResponseEntity<ApiResponse<AdvanceRentResponse>> createAdvance(@PathVariable UUID mid,
                                                                           @Valid @RequestBody CreateAdvanceRentRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Advance rent recorded", advanceService.create(mid, req)));
    }

    @GetMapping("/api/v1/memberships/{mid}/advance-rent")
    public ResponseEntity<ApiResponse<List<AdvanceRentResponse>>> listAdvance(@PathVariable UUID mid) {
        return ResponseEntity.ok(ApiResponse.success("Advance rent fetched", advanceService.listForMembership(mid)));
    }

    @GetMapping("/api/v1/advance-rent/{id}")
    public ResponseEntity<ApiResponse<AdvanceRentResponse>> getAdvanceById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Advance rent fetched", advanceService.getById(id)));
    }

    // ── Opening balance — one per membership, immutable ───────

    @PostMapping("/api/v1/memberships/{mid}/opening-balance")
    public ResponseEntity<ApiResponse<OpeningBalanceResponse>> createOpening(@PathVariable UUID mid,
                                                                              @Valid @RequestBody CreateOpeningBalanceRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Opening balance recorded", openingBalanceService.create(mid, req)));
    }

    @GetMapping("/api/v1/memberships/{mid}/opening-balance")
    public ResponseEntity<ApiResponse<OpeningBalanceResponse>> getOpening(@PathVariable UUID mid) {
        return ResponseEntity.ok(ApiResponse.success("Opening balance fetched", openingBalanceService.get(mid)));
    }

    // ── Rent increments (§14.3 T14) ──────────────────────────

    // Atomic: append the row + update the referenced room_assignment.monthly_rent.
    @PostMapping("/api/v1/memberships/{mid}/rent-increments")
    public ResponseEntity<ApiResponse<RentIncrementResponse>> applyIncrement(@PathVariable UUID mid,
                                                                              @Valid @RequestBody CreateRentIncrementRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Rent increment recorded", rentIncrementService.apply(mid, req)));
    }

    @GetMapping("/api/v1/memberships/{mid}/rent-increments")
    public ResponseEntity<ApiResponse<List<RentIncrementResponse>>> listIncrements(@PathVariable UUID mid) {
        return ResponseEntity.ok(ApiResponse.success("Rent increments fetched",
                rentIncrementService.listForMembership(mid)));
    }

    @GetMapping("/api/v1/rent-increments/{id}")
    public ResponseEntity<ApiResponse<RentIncrementResponse>> getIncrementById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Rent increment fetched", rentIncrementService.getById(id)));
    }
}
