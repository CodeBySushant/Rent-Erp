package com.renterp.domain.file.service;

import com.renterp.common.exception.ApiException;
import com.renterp.domain.auth.security.AccessGuard;
import com.renterp.domain.auth.security.AuthUser;
import com.renterp.domain.file.dto.FileResponse;
import com.renterp.domain.file.entity.StoredFile;
import com.renterp.domain.file.entity.StoredFile.FilePurpose;
import com.renterp.domain.file.repository.StoredFileRepository;
import com.renterp.domain.file.storage.FileStorage;
import com.renterp.domain.propertyaccess.entity.PropertyAccess.AccessRole;
import com.renterp.domain.tenancy.entity.TenantPropertyMembership.MembershipStatus;
import com.renterp.domain.tenancy.repository.JoinRequestRepository;
import com.renterp.domain.tenancy.repository.TenantProfileRepository;
import com.renterp.domain.tenancy.repository.TenantPropertyMembershipRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Uploads and downloads. Rules:
 * <ul>
 *   <li>Only signed-in users upload; the uploader owns the file.</li>
 *   <li>The type comes from the bytes (JPEG, PNG, WebP, PDF); photos-only
 *       purposes refuse PDF. Size is capped by {@code app.files.max-bytes}.</li>
 *   <li>The storage key is generated here; the client's file name is kept only
 *       for display, stripped of any path.</li>
 *   <li>A file may be tied to a property. The uploader must have access to it,
 *       or be a tenant with a membership or join request there; a payment QR
 *       needs owner/manager rights.</li>
 *   <li>Readers: the uploader, an admin, anyone with access to the file's
 *       property, and - for a payment QR only - the property's active tenants.
 *       Everything else is 403; files are never public.</li>
 * </ul>
 */
@Service
public class FileService {

    private static final Logger log = LogManager.getLogger(FileService.class);

    private final StoredFileRepository files;
    private final FileStorage storage;
    private final AccessGuard guard;
    private final TenantProfileRepository profiles;
    private final TenantPropertyMembershipRepository memberships;
    private final JoinRequestRepository joinRequests;
    private final long maxBytes;

    public FileService(StoredFileRepository files, FileStorage storage, AccessGuard guard,
                       TenantProfileRepository profiles,
                       TenantPropertyMembershipRepository memberships,
                       JoinRequestRepository joinRequests,
                       @Value("${app.files.max-bytes:5242880}") long maxBytes) {
        this.files = files;
        this.storage = storage;
        this.guard = guard;
        this.profiles = profiles;
        this.memberships = memberships;
        this.joinRequests = joinRequests;
        this.maxBytes = maxBytes;
    }

    public FileResponse upload(MultipartFile file, FilePurpose purpose, UUID propertyId) {
        AuthUser user = guard.requireUser();
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("FILE_EMPTY", "Choose a file to upload.");
        }
        if (file.getSize() > maxBytes) {
            throw tooLarge();
        }
        if (propertyId != null) {
            requireCanAttach(user, purpose, propertyId);
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw ApiException.badRequest("FILE_UNREADABLE", "The file could not be read. Try again.");
        }
        if (bytes.length == 0) {
            throw ApiException.badRequest("FILE_EMPTY", "Choose a file to upload.");
        }
        if (bytes.length > maxBytes) {
            throw tooLarge();
        }
        String type = FileTypeSniffer.detect(bytes).orElseThrow(() -> ApiException.badRequest(
                "FILE_TYPE_NOT_ALLOWED", "Only JPG, PNG, WebP or PDF files can be uploaded."));
        if (purpose.imagesOnly() && FileTypeSniffer.PDF.equals(type)) {
            throw ApiException.badRequest("FILE_TYPE_NOT_ALLOWED", "This upload must be a photo (JPG, PNG or WebP).");
        }

        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        String key = String.format("%04d/%02d/%s", now.getYear(), now.getMonthValue(), UUID.randomUUID());
        try (InputStream in = new ByteArrayInputStream(bytes)) {
            storage.store(key, in, bytes.length, type);
        } catch (IOException e) {
            log.error("Storing upload failed — key: {}", key, e);
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "STORAGE_UNAVAILABLE",
                    "The file could not be saved right now. Try again.");
        }

        try {
            StoredFile saved = files.save(StoredFile.builder()
                    .ownerUserId(user.userId())
                    .propertyId(propertyId)
                    .purpose(purpose)
                    .contentType(type)
                    .sizeBytes(bytes.length)
                    .sha256(sha256(bytes))
                    .storageKey(key)
                    .originalName(cleanName(file.getOriginalFilename()))
                    .createdAt(Instant.now())
                    .build());
            log.info("File uploaded — id: {}, purpose: {}, type: {}, bytes: {}",
                    saved.getId(), purpose, type, bytes.length);
            return FileResponse.from(saved);
        } catch (RuntimeException e) {
            // The metadata row is the only pointer to the bytes: without it they are orphaned.
            deleteQuietly(key);
            throw e;
        }
    }

    public FileResponse metadata(UUID id) {
        return FileResponse.from(readable(id));
    }

    /** The file and an open stream of its bytes; the caller closes the stream. */
    public Download download(UUID id) {
        StoredFile f = readable(id);
        try {
            return new Download(f, storage.open(f.getStorageKey()));
        } catch (IOException e) {
            log.error("Stored bytes missing — file: {}, key: {}", f.getId(), f.getStorageKey(), e);
            throw ApiException.notFound("FILE_NOT_FOUND", "This file is no longer available.");
        }
    }

    public void delete(UUID id) {
        AuthUser user = guard.requireUser();
        StoredFile f = files.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> ApiException.notFound("FILE_NOT_FOUND", "File not found."));
        if (!user.isAdmin() && !user.userId().equals(f.getOwnerUserId())) {
            throw ApiException.forbidden("Only the person who uploaded a file can delete it.");
        }
        f.setDeletedAt(Instant.now());
        files.save(f);
        deleteQuietly(f.getStorageKey());
        log.info("File deleted — id: {}", id);
    }

    public record Download(StoredFile file, InputStream content) {
    }

    // ── Rules ───────────────────────────────────────────────────────────────

    private StoredFile readable(UUID id) {
        AuthUser user = guard.requireUser();
        StoredFile f = files.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> ApiException.notFound("FILE_NOT_FOUND", "File not found."));
        if (!canRead(user, f)) {
            throw ApiException.forbidden("You do not have access to this file.");
        }
        return f;
    }

    private boolean canRead(AuthUser user, StoredFile f) {
        if (user.isAdmin() || user.userId().equals(f.getOwnerUserId())) {
            return true;
        }
        if (f.getPropertyId() == null) {
            return false;
        }
        if (guard.hasPropertyAccess(f.getPropertyId(), AccessRole.VIEW_ONLY)) {
            return true;
        }
        // Tenants pay by scanning the owner's QR, so they may see that one file type.
        return f.getPurpose() == FilePurpose.PAYMENT_QR && isActiveTenant(user, f.getPropertyId());
    }

    private void requireCanAttach(AuthUser user, FilePurpose purpose, UUID propertyId) {
        if (user.isAdmin()) {
            return;
        }
        if (purpose == FilePurpose.PAYMENT_QR) {
            guard.requirePropertyAccess(propertyId, AccessRole.MANAGER);
            return;
        }
        if (guard.hasPropertyAccess(propertyId, AccessRole.VIEW_ONLY) || isTenantOf(user, propertyId)) {
            return;
        }
        throw ApiException.forbidden("You do not have access to this property.");
    }

    /** A membership (any status) or a join request at this property. */
    private boolean isTenantOf(AuthUser user, UUID propertyId) {
        return profiles.findByUserId(user.userId())
                .map(p -> memberships.findPropertyIdsByTenantProfileId(p.getId()).contains(propertyId)
                        || joinRequests.findPropertyIdsByTenantProfileId(p.getId()).contains(propertyId))
                .orElse(false);
    }

    private boolean isActiveTenant(AuthUser user, UUID propertyId) {
        return profiles.findByUserId(user.userId())
                .flatMap(p -> memberships.findByTenantProfileIdAndPropertyIdAndStatus(
                        p.getId(), propertyId, MembershipStatus.ACTIVE))
                .isPresent();
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private ApiException tooLarge() {
        return new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE",
                "Files can be at most " + (maxBytes / (1024 * 1024)) + " MB.");
    }

    /** The client's name without any folder part or control characters, at most 100 characters. */
    static String cleanName(String name) {
        if (name == null) {
            return null;
        }
        String base = name.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1);
        base = base.replaceAll("[\\p{Cntrl}\"<>|:*?]", "").trim();
        if (base.isEmpty()) {
            return null;
        }
        return base.length() > 100 ? base.substring(0, 100) : base;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private void deleteQuietly(String key) {
        try {
            storage.delete(key);
        } catch (IOException | RuntimeException e) {
            log.warn("Could not remove stored bytes — key: {}", key, e);
        }
    }
}
