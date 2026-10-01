package com.renterp.domain.auth.security;

import com.renterp.domain.auth.entity.User.UserRole;

import java.util.UUID;

/**
 * The authenticated caller, taken from a verified access token whose session
 * is still active. This is the only source of "who is asking" - request bodies
 * and query parameters never decide identity.
 */
public record AuthUser(UUID userId, UUID sessionId, UserRole role) {

    public boolean isAdmin() {
        return role == UserRole.ADMIN;
    }
}
