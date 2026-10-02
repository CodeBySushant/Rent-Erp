package com.renterp.domain.payment.dto;

import com.renterp.domain.payment.entity.Payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** A payment and, after any change, the bill's new state. */
public record PaymentResponse(UUID id, UUID billId, UUID membershipId, UUID propertyId, BigDecimal amount,
                              String method, String source, String status, String paidAtBs, String reference,
                              String note, UUID proofFileId, String proofUrl, String rejectionReason,
                              Instant decidedAt, Instant createdAt, BillState bill) {

    /** The bill after the payment (null in list responses). */
    public record BillState(BigDecimal totalDue, BigDecimal amountPaid, BigDecimal balanceDue, String paymentStatus) {
    }

    public static PaymentResponse from(Payment p, BillState bill) {
        return new PaymentResponse(p.getId(), p.getBillId(), p.getMembershipId(), p.getPropertyId(), p.getAmount(),
                p.getMethod().name(), p.getSource().name(), p.getStatus().name(), p.getPaidAtBs(), p.getReference(),
                p.getNote(), p.getProofFileId(),
                p.getProofFileId() == null ? null : "/api/v1/files/" + p.getProofFileId() + "/content",
                p.getRejectionReason(), p.getDecidedAt(), p.getCreatedAt(), bill);
    }
}
