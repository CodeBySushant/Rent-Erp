package com.renterp.domain.identity.service;

import com.renterp.common.exception.ApiException;
import com.renterp.domain.auth.entity.User.UserRole;
import com.renterp.domain.auth.repository.UserRepository;
import com.renterp.domain.auth.security.AccessGuard;
import com.renterp.domain.auth.security.AuthUser;
import com.renterp.domain.file.entity.StoredFile;
import com.renterp.domain.file.entity.StoredFile.FilePurpose;
import com.renterp.domain.file.repository.StoredFileRepository;
import com.renterp.domain.identity.dto.IdentityResponse;
import com.renterp.domain.identity.dto.SubmitIdentityRequest;
import com.renterp.domain.identity.entity.OwnerIdentity;
import com.renterp.domain.identity.repository.OwnerIdentityRepository;
import com.renterp.domain.tenancy.entity.TenantKyc.IdType;
import com.renterp.domain.tenancy.repository.TenantKycRepository;
import com.renterp.domain.tenancy.repository.TenantProfileRepository;
import com.renterp.domain.tenancy.service.TenantProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Owner identity is stored, and only from the caller's own, correctly-purposed uploads. */
class IdentityServiceTest {

    private final UUID me = UUID.randomUUID();
    private final UUID front = UUID.randomUUID();
    private final UUID selfie = UUID.randomUUID();

    private AccessGuard guard;
    private OwnerIdentityRepository owners;
    private StoredFileRepository files;
    private IdentityService service;

    @BeforeEach
    void setUp() {
        guard = mock(AccessGuard.class);
        owners = mock(OwnerIdentityRepository.class);
        files = mock(StoredFileRepository.class);
        when(guard.requireUser()).thenReturn(new AuthUser(me, UUID.randomUUID(), UserRole.LANDLORD));
        when(owners.findById(me)).thenReturn(Optional.empty());
        when(owners.saveAndFlush(any(OwnerIdentity.class))).thenAnswer(i -> i.getArgument(0));
        service = new IdentityService(guard, owners, files, mock(UserRepository.class),
                mock(TenantProfileRepository.class), mock(TenantProfileService.class), mock(TenantKycRepository.class));
        stubFile(front, me, FilePurpose.KYC_ID_FRONT);
        stubFile(selfie, me, FilePurpose.KYC_SELFIE);
    }

    private void stubFile(UUID id, UUID owner, FilePurpose purpose) {
        when(files.findByIdAndDeletedAtIsNull(id)).thenReturn(
                Optional.of(StoredFile.builder().ownerUserId(owner).purpose(purpose).build()));
    }

    private SubmitIdentityRequest request() {
        return new SubmitIdentityRequest(IdType.CITIZENSHIP, " 12-34-56789 ", front, null, selfie);
    }

    @Test
    void ownerIdentityIsStoredAndNumberMasked() {
        IdentityResponse r = service.submit(request());
        assertTrue(r.submitted());
        assertEquals("OWNER", r.kind());
        assertEquals("SUBMITTED", r.status());
        assertEquals("CITIZENSHIP", r.idType());
        assertEquals("********789", r.idNumberMasked());
        verify(owners).saveAndFlush(any(OwnerIdentity.class));
    }

    @Test
    void anotherUsersPhotoIsRefused() {
        stubFile(front, UUID.randomUUID(), FilePurpose.KYC_ID_FRONT);
        assertThrows(ApiException.class, () -> service.submit(request()));
        verify(owners, never()).saveAndFlush(any());
    }

    @Test
    void wrongPurposeIsRefused() {
        stubFile(selfie, me, FilePurpose.OTHER);
        assertThrows(ApiException.class, () -> service.submit(request()));
        verify(owners, never()).saveAndFlush(any());
    }

    @Test
    void nothingStoredYetMeansNotSubmitted() {
        IdentityResponse r = service.mine();
        assertFalse(r.submitted());
        assertEquals("NOT_SUBMITTED", r.status());
        assertNull(r.idNumberMasked());
    }

    @Test
    void maskKeepsOnlyTheLastCharacters() {
        assertEquals("*******789", IdentityResponse.mask("1234567789".substring(0, 10)).replace("*", "*"));
        assertNull(IdentityResponse.mask("  "));
    }
}
