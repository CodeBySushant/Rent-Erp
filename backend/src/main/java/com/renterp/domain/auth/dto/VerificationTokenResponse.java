package com.renterp.domain.auth.dto;

/** Single-use proof that the phone was verified; spent by /auth/register. */
public record VerificationTokenResponse(String verificationToken, long expiresInSeconds) {
}
