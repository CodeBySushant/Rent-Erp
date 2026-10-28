package com.renterp.domain.tenancy.service;

import com.renterp.common.exception.ApiException;
import com.renterp.domain.auth.security.AccessGuard;
import com.renterp.domain.auth.security.ResourceAccess;
import com.renterp.domain.file.entity.StoredFile;
import com.renterp.domain.file.entity.StoredFile.FilePurpose;
import com.renterp.domain.file.repository.StoredFileRepository;
import com.renterp.domain.file.service.FileService;
import com.renterp.domain.notification.entity.Notification;
import com.renterp.domain.notification.service.NotificationService;
import com.renterp.domain.property.repository.PropertyRepository;
import com.renterp.domain.tenancy.dto.AgreementResponse;
import com.renterp.domain.tenancy.entity.TenantPropertyMembership;
import com.renterp.domain.tenancy.repository.TenantPropertyMembershipRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * The signed rental agreement of a tenancy (V30).
 *
 * <ul>
 *   <li>Read: the property's owner / managers / viewers, and the tenant of
 *       that tenancy (ResourceAccess.membership READ).</li>
 *   <li>Set / replace / remove: owner or manager (WRITE). The file must be the
 *       caller's own upload with purpose AGREEMENT (PDF or photo); it is tied
 *       to the tenancy's property. A replaced or removed file is deleted.</li>
 *   <li>Setting one notifies the tenant in the app ("Agreement added").</li>
 * </ul>
 * The tenant opens it through GET /api/v1/files/{id}/content, which allows
 * the tenant of a tenancy to read that tenancy's agreement.
 */
@Service
public class TenancyAgreementService {

    private static final Logger log = LogManager.getLogger(TenancyAgreementService.class);

    private final TenantPropertyMembershipRepository memberships;
    private final StoredFileRepository files;
    private final FileService fileService;
    private final ResourceAccess access;
    private final AccessGuard guard;
    private final NotificationService notifications;
    private final PropertyRepository properties;

    public TenancyAgreementService(TenantPropertyMembershipRepository memberships, StoredFileRepository files,
                                   FileService fileService, ResourceAccess access, AccessGuard guard,
                                   NotificationService notifications, PropertyRepository properties) {
        this.memberships = memberships;
        this.files = files;
        this.fileService = fileService;
        this.access = access;
        this.guard = guard;
        this.notifications = notifications;
        this.properties = properties;
    }

    /** The agreement, or null when the tenancy has none. 404 TENANCY_NOT_FOUND. */
    @Transactional(readOnly = true)
    public AgreementResponse get(UUID membershipId) {
        access.membership(membershipId, ResourceAccess.Level.READ);
        TenantPropertyMembership m = membership(membershipId);
        return describe(m);
    }

    /**
     * Attaches [fileId] as the agreement, replacing any earlier one.
     * 400 FILE_INVALID (not an AGREEMENT upload), 403 (someone else's file),
     * 404 FILE_NOT_FOUND / TENANCY_NOT_FOUND, 400 FILE_OTHER_PROPERTY.
     */
    @Transactional
    public AgreementResponse set(UUID membershipId, UUID fileId) {
        access.membership(membershipId, ResourceAccess.Level.WRITE);
        UUID me = guard.requireUser().userId();
        TenantPropertyMembership m = membership(membershipId);
        StoredFile f = files.findByIdAndDeletedAtIsNull(fileId)
                .orElseThrow(() -> ApiException.notFound("FILE_NOT_FOUND", "File not found."));
        if (!me.equals(f.getOwnerUserId())) {
            throw ApiException.forbidden("Use a file you uploaded yourself.");
        }
        if (f.getPurpose() != FilePurpose.AGREEMENT) {
            throw ApiException.badRequest("FILE_INVALID", "Upload the document as an agreement first.");
        }
        fileService.attachToProperty(f, m.getPropertyId());

        UUID previous = m.getAgreementFileId();
        m.setAgreementFileId(f.getId());
        m.setAgreementUploadedAt(Instant.now());
        m.setAgreementUploadedBy(me);
        TenantPropertyMembership saved = memberships.save(m);
        if (previous != null && !previous.equals(f.getId())) {
            fileService.discard(previous);
        }
        String propertyName = properties.findById(m.getPropertyId()).map(p -> p.getName()).orElse(null);
        notifications.toTenant(m.getId(), Notification.Type.AGREEMENT_ADDED, propertyName, "AGREEMENT", m.getId());
        log.info("Agreement set — tenancy: {}, file: {}, replaced: {}", m.getId(), f.getId(), previous != null);
        return AgreementResponse.of(saved, f);
    }

    /** Removes the agreement and deletes its file. Nothing to remove is fine. */
    @Transactional
    public void remove(UUID membershipId) {
        access.membership(membershipId, ResourceAccess.Level.WRITE);
        TenantPropertyMembership m = membership(membershipId);
        UUID previous = m.getAgreementFileId();
        if (previous == null) {
            return;
        }
        m.setAgreementFileId(null);
        m.setAgreementUploadedAt(null);
        m.setAgreementUploadedBy(null);
        memberships.save(m);
        fileService.discard(previous);
        log.info("Agreement removed — tenancy: {}", m.getId());
    }

    /** For other services (My Stay): the agreement of [m], or null. No access check. */
    @Transactional(readOnly = true)
    public AgreementResponse describe(TenantPropertyMembership m) {
        if (m.getAgreementFileId() == null) {
            return null;
        }
        return AgreementResponse.of(m, files.findByIdAndDeletedAtIsNull(m.getAgreementFileId()).orElse(null));
    }

    private TenantPropertyMembership membership(UUID id) {
        return memberships.findById(id)
                .orElseThrow(() -> ApiException.notFound("TENANCY_NOT_FOUND", "Tenancy not found."));
    }
}
