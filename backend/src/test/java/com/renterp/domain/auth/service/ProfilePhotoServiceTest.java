package com.renterp.domain.auth.service;

import com.renterp.common.exception.ApiException;
import com.renterp.domain.auth.dto.UserResponse;
import com.renterp.domain.auth.entity.User;
import com.renterp.domain.auth.entity.User.UserRole;
import com.renterp.domain.auth.repository.UserRepository;
import com.renterp.domain.auth.security.AccessGuard;
import com.renterp.domain.auth.security.AuthUser;
import com.renterp.domain.file.entity.StoredFile;
import com.renterp.domain.file.entity.StoredFile.FilePurpose;
import com.renterp.domain.file.repository.StoredFileRepository;
import com.renterp.domain.file.service.FileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** A profile photo is only ever the caller's own PROFILE_PHOTO upload. */
class ProfilePhotoServiceTest {

    private final UUID me = UUID.randomUUID();
    private final UUID photo = UUID.randomUUID();

    private UserRepository users;
    private StoredFileRepository files;
    private FileService fileService;
    private ProfilePhotoService service;
    private User user;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        files = mock(StoredFileRepository.class);
        fileService = mock(FileService.class);
        AccessGuard guard = mock(AccessGuard.class);
        when(guard.requireUser()).thenReturn(new AuthUser(me, UUID.randomUUID(), UserRole.TENANT));
        user = User.builder().id(me).phone("9800000001").role(UserRole.TENANT).build();
        when(users.findById(me)).thenReturn(Optional.of(user));
        when(users.saveAndFlush(any(User.class))).thenAnswer(i -> i.getArgument(0));
        service = new ProfilePhotoService(users, files, fileService, guard);
    }

    private void stubFile(UUID id, UUID owner, FilePurpose purpose) {
        when(files.findByIdAndDeletedAtIsNull(id)).thenReturn(Optional.of(
                StoredFile.builder().id(id).ownerUserId(owner).purpose(purpose).build()));
    }

    @Test
    void ownProfilePhotoBecomesThePhoto() {
        stubFile(photo, me, FilePurpose.PROFILE_PHOTO);
        UserResponse r = service.setMine(photo);
        assertEquals(photo, user.getPhotoFileId());
        assertEquals("/api/v1/files/" + photo + "/content", r.getPhotoUrl());
        verify(fileService, never()).delete(any());
    }

    @Test
    void replacingDeletesTheOldPhoto() {
        UUID old = UUID.randomUUID();
        user.setPhotoFileId(old);
        stubFile(photo, me, FilePurpose.PROFILE_PHOTO);
        service.setMine(photo);
        verify(fileService).delete(old);
    }

    @Test
    void someoneElsesUploadIsRefused() {
        stubFile(photo, UUID.randomUUID(), FilePurpose.PROFILE_PHOTO);
        ApiException e = assertThrows(ApiException.class, () -> service.setMine(photo));
        assertEquals(HttpStatus.FORBIDDEN, e.getStatus());
        assertNull(user.getPhotoFileId());
    }

    @Test
    void otherPurposeIsRefused() {
        stubFile(photo, me, FilePurpose.PAYMENT_PROOF);
        ApiException e = assertThrows(ApiException.class, () -> service.setMine(photo));
        assertEquals("FILE_INVALID", e.getCode());
    }

    @Test
    void missingFileIs404() {
        when(files.findByIdAndDeletedAtIsNull(photo)).thenReturn(Optional.empty());
        ApiException e = assertThrows(ApiException.class, () -> service.setMine(photo));
        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
    }

    @Test
    void removeClearsAndDeletes() {
        user.setPhotoFileId(photo);
        UserResponse r = service.removeMine();
        assertNull(user.getPhotoFileId());
        assertNull(r.getPhotoUrl());
        verify(fileService).delete(photo);
    }

    @Test
    void urlsOnlyForAccountsWithAPhoto() {
        UUID other = UUID.randomUUID();
        user.setPhotoFileId(photo);
        User none = User.builder().id(other).phone("9800000002").role(UserRole.TENANT).build();
        when(users.findAllById(any())).thenReturn(List.of(user, none));
        Map<UUID, String> urls = service.urlsOf(List.of(me, other));
        assertEquals(Map.of(me, "/api/v1/files/" + photo + "/content"), urls);
    }
}
