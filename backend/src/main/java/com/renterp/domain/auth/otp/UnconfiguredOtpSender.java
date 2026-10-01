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
        throw ApiException.unavailable("SMS_NOT_CONFIGURED",
                "Text messages cannot be sent yet. Log in with your email and password.");
    }
}
