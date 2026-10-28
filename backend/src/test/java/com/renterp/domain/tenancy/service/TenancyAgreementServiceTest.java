package com.renterp.domain.tenancy.service;

import com.renterp.common.exception.ApiException;
import com.renterp.domain.auth.entity.User.UserRole;
import com.renterp.domain.auth.security.AccessGuard;
import com.renterp.domain.auth.security.AuthUser;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The signed agreement of a tenancy: who may set it, replacing, removing. */
class TenancyAgreementServiceTest {

    private final UUID me = UUID.randomUUID();
    private final UUID membershipId = UUID.randomUUID();
    private final UUID propertyId = UUID.randomUUID();
    private final UUID fileId = UUID.randomUUID();

    private TenantPropertyMembershipRepository memberships;
    private StoredFileRepository files;
    private FileService fileService;
    private ResourceAccess access;
    private NotificationService notifications;
    private TenancyAgreementService service;
    private TenantPropertyMembership membership;

    @BeforeEach
    void setUp() {
        memberships = mock(TenantPropertyMembershipRepository.class);
        files = mock(StoredFileRepository.class);
        fileService = mock(FileService.class);
        access = mock(ResourceAccess.class);
        notifications = mock(NotificationService.class);
        AccessGuard guard = mock(AccessGuard.class);
        when(guard.requireUser()).thenReturn(new AuthUser(me, UUID.randomUUID(), UserRole.LANDLORD));
        service = new TenancyAgreementService(memberships, files, fileService, access, guard, notifications,
                mock(PropertyRepository.class));
        membership = TenantPropertyMembership.builder().id(membershipId).propertyId(propertyId)
                .tenantProfileId(UUID.randomUUID()).startedAtBs("2082-01-01").build();
        when(memberships.findById(membershipId)).thenReturn(Optional.of(membership));
        when(memberships.save(any(TenantPropertyMembership.class))).thenAnswer(i -> i.getArgument(0));
    }

    private StoredFile stubFile(UUID id, UUID owner, FilePurpose purpose) {
        StoredFile f = StoredFile.builder().id(id).ownerUserId(owner).purpose(purpose)
                .contentType("application/pdf").sizeBytes(1234).originalName("agreement.pdf").build();
        when(files.findByIdAndDeletedAtIsNull(id)).thenReturn(Optional.of(f));
        return f;
    }

    @Test
    void ownersAgreementUploadIsAttachedAndTheTenantNotified() {
        StoredFile f = stubFile(fileId, me, FilePurpose.AGREEMENT);
        AgreementResponse r = service.set(membershipId, fileId);

        verify(access).membership(membershipId, ResourceAccess.Level.WRITE);
        verify(fileService).attachToProperty(f, propertyId);
        assertEquals(fileId, membership.getAgreementFileId());
        assertEquals(me, membership.getAgreementUploadedBy());
        assertNotNull(membership.getAgreementUploadedAt());
        assertEquals("/api/v1/files/" + fileId + "/content", r.url());
        assertEquals("application/pdf", r.contentType());
        assertEquals("agreement.pdf", r.fileName());
        verify(notifications).toTenant(any(), any(), any(), any(), any());
        verify(notifications).toTenant(membershipId, Notification.Type.AGREEMENT_ADDED, null, "AGREEMENT",
                membershipId);
        verify(fileService, never()).discard(any());
    }

    @Test
    void replacingDeletesTheOldFile() {
        UUID old = UUID.randomUUID();
        membership.setAgreementFileId(old);
        stubFile(fileId, me, FilePurpose.AGREEMENT);
        service.set(membershipId, fileId);
        verify(fileService).discard(old);
        assertEquals(fileId, membership.getAgreementFileId());
    }

    @Test
    void onlyYourOwnAgreementUploadCanBeUsed() {
        stubFile(fileId, UUID.randomUUID(), FilePurpose.AGREEMENT);
        assertThrows(ApiException.class, () -> service.set(membershipId, fileId));

        stubFile(fileId, me, FilePurpose.PAYMENT_PROOF);
        ApiException e = assertThrows(ApiException.class, () -> service.set(membershipId, fileId));
        assertEquals("FILE_INVALID", e.getCode());
        assertNull(membership.getAgreementFileId());
        verify(notifications, never()).toTenant(any(), any(), any(), any(), any());
    }

    @Test
    void writeAccessIsRequired() {
        doThrow(ApiException.forbidden("no")).when(access).membership(membershipId, ResourceAccess.Level.WRITE);
        assertThrows(ApiException.class, () -> service.set(membershipId, fileId));
        assertThrows(ApiException.class, () -> service.remove(membershipId));
        verify(memberships, never()).save(any());
    }

    @Test
    void removeClearsAndDeletesTheFile() {
        UUID old = UUID.randomUUID();
        membership.setAgreementFileId(old);
        service.remove(membershipId);
        assertNull(membership.getAgreementFileId());
        assertNull(membership.getAgreementUploadedAt());
        verify(fileService).discard(old);
    }

    @Test
    void getIsNullWithoutAnAgreementAndChecksReadAccess() {
        assertNull(service.get(membershipId));
        verify(access).membership(membershipId, ResourceAccess.Level.READ);
    }

    @Test
    void unknownTenancyIs404() {
        UUID unknown = UUID.randomUUID();
        when(memberships.findById(unknown)).thenReturn(Optional.empty());
        ApiException e = assertThrows(ApiException.class, () -> service.get(unknown));
        assertEquals("TENANCY_NOT_FOUND", e.getCode());
    }
}
