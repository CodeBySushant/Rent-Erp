package com.renterp.domain.identity.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * What the app needs to know about the caller's identity. {@code status} is
 * NOT_SUBMITTED, SUBMITTED (owner: stored, never reviewed) or - for tenants,
 * whose submissions are reviewed - PENDING, APPROVED, REJECTED, FLAGGED.
 * The ID number is masked; the full number is only in the admin view.
 */
public record IdentityResponse(
        boolean submitted,
        String kind,
        String status,
        String idType,
        String idNumberMasked,
        UUID idFrontFileId,
        UUID idBackFileId,
        UUID selfieFileId,
        Instant submittedAt,
        String rejectionReason) {

    public static IdentityResponse none(String kind) {
        return new IdentityResponse(false, kind, "NOT_SUBMITTED", null, null, null, null, null, null, null);
    }

    /** "12-34-56789" -> "*******789": enough to recognise, not enough to misuse. */
    public static String mask(String idNumber) {
        if (idNumber == null || idNumber.isBlank()) return null;
        String s = idNumber.trim();
        int keep = Math.min(3, s.length() / 2);
        return "*".repeat(s.length() - keep) + s.substring(s.length() - keep);
    }
}
