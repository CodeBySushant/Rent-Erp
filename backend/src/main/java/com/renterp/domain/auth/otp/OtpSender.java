package com.renterp.domain.auth.otp;

import com.renterp.domain.auth.entity.OtpAttempt;

/**
 * Delivers a one-time code. One implementation is active per environment:
 * {@link LocalLogOtpSender} when APP_ENV=local (the code goes to the
 * backend log, never to the API response), {@link UnconfiguredOtpSender}
 * everywhere else until an SMS provider (Sparrow SMS) is added here.
 *
 * Codes for an email address go by email instead when MAIL_HOST is set
 * (OtpService + MailGateway); this sender only sees them otherwise.
 */
public interface OtpSender {

    /** {@code phone} is the destination: a phone number or an email address. */
    void send(String phone, String code, OtpAttempt.Purpose purpose);

    /** False when this sender refuses every code (no provider configured). */
    default boolean available() {
        return true;
    }
}
