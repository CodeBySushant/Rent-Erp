package com.renterp.domain.auth.dto;

import com.renterp.domain.auth.entity.User;
import com.renterp.domain.auth.entity.User.KycStatus;
import com.renterp.domain.auth.entity.User.UserRole;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
public class UserResponse {

    private final UUID id;
    private final String phone;
    private final String email;
    private final String name;
    private final UserRole role;
    private final KycStatus kycStatus;
    private final boolean active;
    private final String preferredLanguage;

    /** {@code /api/v1/files/{id}/content} of the current photo, or null. */
    private final String photoUrl;
    private final Instant createdAt;
    private final Instant updatedAt;

    private UserResponse(User user) {
        this.id = user.getId();
        this.phone = user.getPhone();
        this.email = user.getEmail();
        this.name = user.getName();
        this.role = user.getRole();
        this.kycStatus = user.getKycStatus();
        this.active = user.isActive();
        this.preferredLanguage = user.getPreferredLanguage();
        this.photoUrl = photoUrl(user.getPhotoFileId());
        this.createdAt = user.getCreatedAt();
        this.updatedAt = user.getUpdatedAt();
    }

    public static UserResponse from(User user) {
        return new UserResponse(user);
    }

    /** The download path the app shows a profile photo from. */
    public static String photoUrl(UUID fileId) {
        return fileId == null ? null : "/api/v1/files/" + fileId + "/content";
    }
}
