package com.renterp.domain.tenancy.controller;

import com.renterp.common.response.ApiResponse;
import com.renterp.domain.tenancy.dto.AgreementResponse;
import com.renterp.domain.tenancy.service.TenancyAgreementService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** The signed rental agreement of a tenancy (V30). */
@RestController
public class TenancyAgreementController {

    private final TenancyAgreementService service;

    public TenancyAgreementController(TenancyAgreementService service) {
        this.service = service;
    }

    /** Owner side and the tenant themself; data is null when there is none. */
    @GetMapping("/api/v1/memberships/{id}/agreement")
    public ResponseEntity<ApiResponse<AgreementResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Agreement fetched", service.get(id)));
    }

    /** Owner or manager: {fileId} of their own AGREEMENT upload (PDF or photo). */
    @PutMapping("/api/v1/memberships/{id}/agreement")
    public ResponseEntity<ApiResponse<AgreementResponse>> set(@PathVariable UUID id,
                                                              @Valid @RequestBody SetAgreement req) {
        return ResponseEntity.ok(ApiResponse.success("Agreement saved", service.set(id, req.getFileId())));
    }

    /** Owner or manager: removes it and deletes the file. */
    @DeleteMapping("/api/v1/memberships/{id}/agreement")
    public ResponseEntity<ApiResponse<Void>> remove(@PathVariable UUID id) {
        service.remove(id);
        return ResponseEntity.ok(ApiResponse.success("Agreement removed", null));
    }

    @Getter
    @Setter
    public static class SetAgreement {
        @NotNull(message = "fileId is required")
        private UUID fileId;
    }
}
