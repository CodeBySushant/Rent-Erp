package com.renterp.domain.auth.service;

import com.renterp.common.exception.ApiException;
import com.renterp.domain.auth.dto.UserResponse;
import com.renterp.domain.auth.entity.User;
import com.renterp.domain.auth.repository.UserRepository;
import com.renterp.domain.auth.security.AccessGuard;
import com.renterp.domain.file.entity.StoredFile;
import com.renterp.domain.file.entity.StoredFile.FilePurpose;
import com.renterp.domain.file.repository.StoredFileRepository;
import com.renterp.domain.file.service.FileService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * The signed-in user's profile photo, and photo URLs for lists.
 *
 * <p>Flow: the app uploads the image with {@code POST /api/v1/files}
 * (purpose {@code PROFILE_PHOTO}, no property), then calls
 * {@code PUT /api/v1/me/photo {fileId}}. The previous photo, if any, is
 * deleted so it no longer counts against the user's storage. Who may see a
 * photo is decided by {@link FileService}.
 */
@Service
public class ProfilePhotoService {

    private static final Logger log = LogManager.getLogger(ProfilePhotoService.class);

    private final UserRepository users;
    private final StoredFileRepository files;
    private final FileService fileService;
    private final AccessGuard guard;

    public ProfilePhotoService(UserRepository users, StoredFileRepository files,
                               FileService fileService, AccessGuard guard) {
        this.users = users;
        this.files = files;
        this.fileService = fileService;
        this.guard = guard;
    }

    /** Makes [fileId] (the caller's own PROFILE_PHOTO upload) their photo. */
    @Transactional
    public UserResponse setMine(UUID fileId) {
        UUID me = guard.requireUser().userId();
        StoredFile f = files.findByIdAndDeletedAtIsNull(fileId)
                .orElseThrow(() -> ApiException.notFound("FILE_NOT_FOUND", "File not found."));
        if (!me.equals(f.getOwnerUserId())) {
            throw ApiException.forbidden("Use a photo you uploaded yourself.");
        }
        if (f.getPurpose() != FilePurpose.PROFILE_PHOTO) {
            throw ApiException.badRequest("FILE_INVALID", "Upload the photo as a profile photo first.");
        }
        User user = current(me);
        UUID previous = user.getPhotoFileId();
        user.setPhotoFileId(f.getId());
        User saved = users.saveAndFlush(user);
        if (previous != null && !previous.equals(f.getId())) {
            removeOld(previous);
        }
        log.info("Profile photo set — user: {}, file: {}", me, f.getId());
        return UserResponse.from(saved);
    }

    /** Clears the caller's photo (and deletes the file). */
    @Transactional
    public UserResponse removeMine() {
        UUID me = guard.requireUser().userId();
        User user = current(me);
        UUID previous = user.getPhotoFileId();
        user.setPhotoFileId(null);
        User saved = users.saveAndFlush(user);
        if (previous != null) {
            removeOld(previous);
        }
        log.info("Profile photo removed — user: {}", me);
        return UserResponse.from(saved);
    }

    /** Photo URL of each account in [userIds] that has one. Nulls are ignored. */
    @Transactional(readOnly = true)
    public Map<UUID, String> urlsOf(Collection<UUID> userIds) {
        Map<UUID, String> out = new HashMap<>();
        var ids = userIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return out;
        }
        for (User u : users.findAllById(ids)) {
            if (u.getPhotoFileId() != null) {
                out.put(u.getId(), UserResponse.photoUrl(u.getPhotoFileId()));
            }
        }
        return out;
    }

    /** Photo URL of one account, or null. */
    @Transactional(readOnly = true)
    public String urlOf(UUID userId) {
        return userId == null ? null : urlsOf(java.util.List.of(userId)).get(userId);
    }

    private User current(UUID id) {
        return users.findById(id)
                .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "User not found."));
    }

    private void removeOld(UUID fileId) {
        try {
            fileService.delete(fileId);
        } catch (RuntimeException e) {
            // The new photo is saved; a leftover old file is only storage.
            log.warn("Old profile photo not removed — file: {}", fileId, e);
        }
    }
}
