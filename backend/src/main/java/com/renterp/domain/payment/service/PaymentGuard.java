package com.renterp.domain.payment.service;

import com.renterp.common.exception.ApiException;
import com.renterp.domain.payment.entity.Payment.Status;
import com.renterp.domain.payment.repository.PaymentRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/** Payment-state rules other domains must respect. */
@Component
public class PaymentGuard {

    private final PaymentRepository payments;

    public PaymentGuard(PaymentRepository payments) {
        this.payments = payments;
    }

    /**
     * A billing run whose bills have approved or pending payments cannot be
     * cancelled: the money would be left pointing at cancelled bills.
     */
    public void requireNoPaymentsOnRun(UUID runId) {
        if (payments.countForRun(runId, List.of(Status.APPROVED, Status.PENDING)) > 0) {
            throw ApiException.conflict("RUN_HAS_PAYMENTS",
                    "Payments have been made against these bills, so they cannot be cancelled.");
        }
    }
}
