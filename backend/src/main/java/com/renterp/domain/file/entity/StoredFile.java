package com.renterp.domain.file.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/** Metadata for one uploaded file (V15). Append-only apart from the soft delete. */
@Entity
@Table(name = "stored_files")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoredFile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "owner_user_id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID ownerUserId;

    @Column(name = "property_id", columnDefinition = "uuid", updatable = false)
    private UUID propertyId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, updatable = false)
    private FilePurpose purpose;

    @Column(name = "content_type", nullable = false, length = 100, updatable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false, updatable = false)
    private long sizeBytes;

    @Column(nullable = false, length = 64, updatable = false)
    private String sha256;

    @Column(name = "storage_key", nullable = false, length = 255, unique = true, updatable = false)
    private String storageKey;

    @Column(name = "original_name", length = 255, updatable = false)
    private String originalName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public enum FilePurpose {
        KYC_ID_FRONT, KYC_ID_BACK, KYC_SELFIE, METER_PHOTO, PAYMENT_PROOF,
        PAYMENT_QR, PROFILE_PHOTO, AGREEMENT, REQUEST_PHOTO, OTHER;

        /** Photos only: no PDF where the app shows the file as an image. */
        public boolean imagesOnly() {
            return switch (this) {
                case KYC_SELFIE, METER_PHOTO, PAYMENT_QR, PROFILE_PHOTO, REQUEST_PHOTO -> true;
                default -> false;
            };
        }
    }
}
