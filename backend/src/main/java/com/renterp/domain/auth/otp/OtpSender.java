package com.renterp.domain.auth.otp;

import com.renterp.domain.auth.entity.OtpAttempt;

/**
 * Delivers a one-time code. One implementation is active per environment:
 * {@link LocalLogOtpSender} when APP_ENV=local (the code goes to the
 * backend log, never to the API response), {@link UnconfiguredOtpSender}
 * everywhere else until an SMS provider (Sparrow SMS) is added here.
 */
public interface OtpSender {

    void send(String phone, String code, OtpAttempt.Purpose purpose);
}
