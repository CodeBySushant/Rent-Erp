package com.renterp.domain.auth.otp;

import com.renterp.common.exception.ApiException;
import com.renterp.domain.auth.entity.OtpAttempt;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

/**
 * Any environment other than local, until an SMS provider is wired: refuses
 * to send (503) instead of pretending. Replace with a Sparrow SMS sender.
 */
@Component
@ConditionalOnExpression("'${app.env}' != 'local'")
public class UnconfiguredOtpSender implements OtpSender {

    @Override
    public void send(String phone, String code, OtpAttempt.Purpose purpose) {
        if (phone != null && phone.indexOf('@') >= 0) {
            // Only reached when MAIL_HOST is not set either.
            throw ApiException.unavailable("EMAIL_NOT_CONFIGURED",
                    "Emails cannot be sent yet. Try again later or contact support.");
        }
        throw ApiException.unavailable("SMS_NOT_CONFIGURED",
                "Text messages cannot be sent yet. Log in with your email and password.");
    }

    @Override
    public boolean available() {
        return false;
    }
}
