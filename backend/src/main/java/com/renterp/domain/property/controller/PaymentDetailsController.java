package com.renterp.domain.property.controller;

import com.renterp.common.response.ApiResponse;
import com.renterp.domain.auth.security.ResourceAccess;
import com.renterp.domain.property.dto.PaymentDetailsRequest;
import com.renterp.domain.property.dto.PaymentDetailsResponse;
import com.renterp.domain.property.service.PaymentDetailsService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** How tenants pay the owner: managers edit, the property's active tenants read. */
@RestController
public class PaymentDetailsController {

    private final PaymentDetailsService service;
    private final ResourceAccess access;

    public PaymentDetailsController(PaymentDetailsService service, ResourceAccess access) {
        this.service = service;
        this.access = access;
    }

    @GetMapping("/api/v1/properties/{propertyId}/payment-details")
    public ResponseEntity<ApiResponse<PaymentDetailsResponse>> get(@PathVariable UUID propertyId) {
        access.propertyOrActiveTenant(propertyId);
        return ResponseEntity.ok(ApiResponse.success("Payment details fetched", service.get(propertyId).orElse(null)));
    }

    @PutMapping("/api/v1/properties/{propertyId}/payment-details")
    public ResponseEntity<ApiResponse<PaymentDetailsResponse>> save(@PathVariable UUID propertyId,
                                                                    @Valid @RequestBody PaymentDetailsRequest req) {
        access.property(propertyId, ResourceAccess.Level.WRITE);
        return ResponseEntity.ok(ApiResponse.success("Payment details saved", service.save(propertyId, req)));
    }
}
