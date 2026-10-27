package com.renterp.domain.identity.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Admin / developer view of a tenant's identity submission (H6), with the full
 * ID number and the uploaded photo file ids (open them with
 * GET /api/v1/files/{id}/content as an admin).
 */
public record AdminTenantKycResponse(
        UUID kycId,
        UUID tenantProfileId,
        UUID userId,
        String name,
        String phone,
        String idType,
        String idNumber,
        UUID idFrontFileId,
        UUID idBackFileId,
        UUID selfieFileId,
        String status,
        Instant submittedAt,
        Instant verifiedAt,
        UUID verifiedBy,
        String rejectionReason,
        String flagReason) {
}
