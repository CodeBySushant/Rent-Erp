package com.renterp.domain.file.dto;

import com.renterp.domain.file.entity.StoredFile;

import java.time.Instant;
import java.util.UUID;

/**
 * What the client gets back after an upload. {@code url} is what other
 * requests store (KYC photo, reading photo, payment proof ...); fetching it
 * needs the same bearer token as every other call.
 */
public record FileResponse(UUID id, String purpose, String contentType, long sizeBytes,
                           UUID propertyId, String originalName, String url, Instant createdAt) {

    public static FileResponse from(StoredFile f) {
        return new FileResponse(f.getId(), f.getPurpose().name(), f.getContentType(), f.getSizeBytes(),
                f.getPropertyId(), f.getOriginalName(), "/api/v1/files/" + f.getId() + "/content",
                f.getCreatedAt());
    }
}
