package com.renterp.domain.identity.dto;

import java.time.Instant;
import java.util.UUID;

/** Admin / developer view of a stored owner identity, with the full ID number. */
public record AdminOwnerIdentityResponse(
        UUID userId,
        String name,
        String phone,
        String idType,
        String idNumber,
        UUID idFrontFileId,
        UUID idBackFileId,
        UUID selfieFileId,
        Instant submittedAt) {
}
