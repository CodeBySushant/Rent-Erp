package com.renterp.domain.auth.service;

import com.renterp.common.exception.ApiException;
import com.renterp.domain.auth.dto.AuthResponse;
import com.renterp.domain.auth.dto.UserResponse;
import com.renterp.domain.auth.entity.User;
import com.renterp.domain.auth.entity.UserSession;
import com.renterp.domain.auth.repository.UserSessionRepository;
import com.renterp.domain.auth.security.AuthUser;
import com.renterp.domain.auth.security.JwtService;
import com.renterp.domain.auth.security.TokenHasher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Sessions and tokens. One {@code user_sessions} row per logged-in device:
 * it holds the SHA-256 of the refresh token (rotated on every refresh) and its
 * expiry. Access tokens are 15-minute JWTs naming the session, and the filter
 * checks the session on every request - so logout takes effect immediately.
 */
@Service
public class AuthTokenService {

    private final UserSessionRepository sessionRepository;
    private final JwtService jwtService;
    private final Duration refreshTtl;

    public AuthTokenService(UserSessionRepository sessionRepository, JwtService jwtService,
                            @Value("${app.auth.refresh-token-days:30}") long refreshTokenDays) {
        this.sessionRepository = sessionRepository;
        this.jwtService = jwtService;
        this.refreshTtl = Duration.ofDays(refreshTokenDays);
    }

    /** Opens a new session for {@code user} and returns its tokens. */
    @Transactional
    public AuthResponse openSession(User user, String userAgent) {
        if (!user.isActive()) {
            throw ApiException.forbidden("This account has been deactivated.");
        }
        Instant now = Instant.now();
        String refreshToken = TokenHasher.randomToken(32);
        Map<String, Object> device = new HashMap<>();
        if (userAgent != null && !userAgent.isBlank()) {
            device.put("userAgent", userAgent.length() > 255 ? userAgent.substring(0, 255) : userAgent);
        }
        UserSession session = sessionRepository.saveAndFlush(UserSession.builder()
                .user(user)
                .tokenHash(TokenHasher.sha256Hex(refreshToken))
                .deviceInfo(device.isEmpty() ? null : device)
                .expiresAt(now.plus(refreshTtl))
                .lastUsedAt(now)
                .build());
        return response(user, session.getId(), refreshToken);
    }

    /**
     * Exchanges a refresh token for a new access token and a new refresh token.
     * The old refresh token stops working at once (row lock + hash replaced),
     * so a stolen one cannot be used after the real app has refreshed.
     */
    @Transactional
    public AuthResponse rotate(String refreshToken) {
        UserSession session = sessionRepository.findByTokenHashForUpdate(TokenHasher.sha256Hex(refreshToken))
                .orElseThrow(AuthTokenService::invalidRefresh);
        if (!session.isValid()) {
            throw invalidRefresh();
        }
        User user = session.getUser();
        if (!user.isActive()) {
            session.setRevokedAt(Instant.now());
            sessionRepository.save(session);
            throw ApiException.unauthorized("ACCOUNT_DISABLED", "This account has been deactivated.");
        }
        Instant now = Instant.now();
        String next = TokenHasher.randomToken(32);
        session.setTokenHash(TokenHasher.sha256Hex(next));
        session.setLastUsedAt(now);
        session.setExpiresAt(now.plus(refreshTtl));
        sessionRepository.save(session);
        return response(user, session.getId(), next);
    }

    /** Ends one session (logout). Only the session's own user can end it. */
    @Transactional
    public void revoke(UUID sessionId, UUID userId) {
        sessionRepository.findById(sessionId)
                .filter(s -> s.getUser().getId().equals(userId))
                .filter(s -> s.getRevokedAt() == null)
                .ifPresent(s -> {
                    s.setRevokedAt(Instant.now());
                    sessionRepository.save(s);
                });
    }

    private AuthResponse response(User user, UUID sessionId, String refreshToken) {
        String access = jwtService.issue(new AuthUser(user.getId(), sessionId, user.getRole()));
        return new AuthResponse(UserResponse.from(user), access, refreshToken, "Bearer",
                jwtService.accessTokenTtl().toSeconds(), refreshTtl.toSeconds());
    }

    private static ApiException invalidRefresh() {
        return ApiException.unauthorized("REFRESH_INVALID", "Your session has ended. Please log in again.");
    }
}
