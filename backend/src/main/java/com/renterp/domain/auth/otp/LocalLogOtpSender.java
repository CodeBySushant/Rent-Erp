package com.renterp.domain.auth.otp;

import com.renterp.domain.auth.entity.OtpAttempt;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

/**
 * Development only (APP_ENV=local): writes the code to the backend log, where
 * the developer (and the auth API test script) reads it. This bean does not
 * exist in any other environment, so codes can never be logged in production.
 */
@Component
@ConditionalOnExpression("'${app.env}' == 'local'")
public class LocalLogOtpSender implements OtpSender {

    private static final Logger log = LogManager.getLogger(LocalLogOtpSender.class);

    @Override
    public void send(String phone, String code, OtpAttempt.Purpose purpose) {
        log.warn("DEV OTP for {} ({}): {}", phone, purpose, code);
    }
}
