package com.renterp.domain.auth.dto;

/**
 * Returned by register and every login / refresh. The refresh token is shown
 * once and stored only as a hash; it is rotated on every refresh.
 */
public record AuthResponse(
        UserResponse user,
        String accessToken,
        String refreshToken,
        String tokenType,
        long accessTokenExpiresInSeconds,
        long refreshTokenExpiresInSeconds) {
}
