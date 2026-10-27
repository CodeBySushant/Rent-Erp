package com.renterp.domain.identity.dto;

import com.renterp.domain.tenancy.entity.TenantKyc.IdType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * The signed-in user's identity: an ID type and number plus photos that were
 * uploaded first through {@code POST /api/v1/files} (purposes KYC_ID_FRONT,
 * KYC_ID_BACK, KYC_SELFIE). The same request serves owners and tenants.
 */
public record SubmitIdentityRequest(
        @NotNull(message = "idType is required") IdType idType,
        @NotBlank(message = "idNumber is required")
        @Size(min = 4, max = 100, message = "idNumber must be 4-100 characters") String idNumber,
        @NotNull(message = "idFrontFileId is required") UUID idFrontFileId,
        UUID idBackFileId,
        @NotNull(message = "selfieFileId is required") UUID selfieFileId) {
}
