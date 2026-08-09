package com.renterp.domain.billing.controller;

import com.renterp.common.response.ApiResponse;
import com.renterp.common.response.PagedResponse;
import com.renterp.domain.billing.dto.CreateTariffRequest;
import com.renterp.domain.billing.dto.TariffResponse;
import com.renterp.domain.billing.dto.UpdateTariffRequest;
import com.renterp.domain.billing.service.TariffVersionService;
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

/**
 * NEA tariff schedule reference data (national, not property-scoped). Consumed by the
 * blended-rate billing engine (pass 2); managed here so the schedules exist before a
 * blended-rate run needs them.
 */
@RestController
@RequestMapping("/api/v1/tariffs")
public class TariffController {

    private static final Logger log = LogManager.getLogger(TariffController.class);

    private final TariffVersionService tariffService;

    public TariffController(TariffVersionService tariffService) {
        this.tariffService = tariffService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TariffResponse>> create(@Valid @RequestBody CreateTariffRequest req) {
        log.debug("POST /api/v1/tariffs — effectiveFrom: {}", req.getEffectiveFromBs());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tariff version created", tariffService.create(req)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TariffResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Tariff version fetched", tariffService.getById(id)));
    }

    // The tariff in force on a BS date — GET /api/v1/tariffs/effective?asOfBs=2082-04-15
    @GetMapping("/effective")
    public ResponseEntity<ApiResponse<TariffResponse>> effectiveOn(@RequestParam String asOfBs) {
        return ResponseEntity.ok(ApiResponse.success("Effective tariff fetched", tariffService.effectiveOn(asOfBs)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<TariffResponse>>> list(
            @PageableDefault(size = 20, sort = "effectiveFromBs", direction = Sort.Direction.DESC)
            Pageable pageable) {
        Page<TariffResponse> page = tariffService.list(pageable);
        return ResponseEntity.ok(ApiResponse.success("Tariff versions fetched", PagedResponse.from(page)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<TariffResponse>> update(@PathVariable UUID id,
                                                              @Valid @RequestBody UpdateTariffRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Tariff version updated", tariffService.update(id, req)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        tariffService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Tariff version deactivated"));
    }
}
