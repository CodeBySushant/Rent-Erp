package com.renterp.domain.auth.dto;

import java.util.UUID;

/** What the app needs to show the code boxes: an id to quote back and the timers. */
public record OtpChallengeResponse(UUID verificationId, long expiresInSeconds, long resendAfterSeconds) {
}
