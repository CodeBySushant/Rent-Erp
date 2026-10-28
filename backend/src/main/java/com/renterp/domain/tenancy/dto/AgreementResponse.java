package com.renterp.domain.tenancy.dto;

import com.renterp.domain.file.entity.StoredFile;
import com.renterp.domain.tenancy.entity.TenantPropertyMembership;

import java.time.Instant;
import java.util.UUID;

/**
 * The signed agreement of a tenancy (V30): the file to open with the bearer
 * token, what kind it is, and when it was added. Null in responses when the
 * tenancy has none.
 *
 * @param url         {@code /api/v1/files/{id}/content}
 * @param contentType application/pdf, image/jpeg, image/png or image/webp
 * @param fileName    the name it was uploaded with, if any
 */
public record AgreementResponse(UUID membershipId, UUID fileId, String url, String contentType, String fileName,
                                long sizeBytes, Instant uploadedAt) {

    /** Null when [f] is missing (no agreement, or its file was deleted). */
    public static AgreementResponse of(TenantPropertyMembership m, StoredFile f) {
        if (m.getAgreementFileId() == null || f == null || f.getDeletedAt() != null) {
            return null;
        }
        return new AgreementResponse(m.getId(), f.getId(), "/api/v1/files/" + f.getId() + "/content",
                f.getContentType(), f.getOriginalName(), f.getSizeBytes(), m.getAgreementUploadedAt());
    }
}
