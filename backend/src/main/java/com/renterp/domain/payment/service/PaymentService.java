package com.renterp.domain.payment.service;

import com.renterp.common.exception.ApiException;
import com.renterp.common.util.BsCalendar;
import com.renterp.domain.auth.security.AccessGuard;
import com.renterp.domain.auth.security.AuthUser;
import com.renterp.domain.billing.entity.TenantBill;
import com.renterp.domain.billing.repository.TenantBillRepository;
import com.renterp.domain.file.entity.StoredFile;
import com.renterp.domain.file.entity.StoredFile.FilePurpose;
import com.renterp.domain.file.repository.StoredFileRepository;
import com.renterp.domain.payment.dto.PaymentResponse;
import com.renterp.domain.payment.dto.PaymentResponse.BillState;
import com.renterp.domain.payment.dto.RecordPaymentRequest;
import com.renterp.domain.payment.entity.Payment;
import com.renterp.domain.payment.entity.Payment.Source;
import com.renterp.domain.payment.entity.Payment.Status;
import com.renterp.domain.payment.repository.PaymentRepository;
import com.renterp.domain.tenancy.repository.TenantProfileRepository;
import com.renterp.domain.tenancy.repository.TenantPropertyMembershipRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Payments against tenant bills.
 *
 * <ul>
 *   <li><b>Owner records</b> money received → APPROVED and applied at once.</li>
 *   <li><b>Tenant sends proof</b> → PENDING; the bill does not change.</li>
 *   <li><b>Owner approves</b> → applied; <b>rejects</b> (with a reason) → REJECTED;
 *       the <b>tenant withdraws</b> while pending → CANCELLED.</li>
 * </ul>
 *
 * A payment changes a bill exactly once: applying happens only on the move to
 * APPROVED, in one transaction holding row locks on the payment and the bill,
 * and the database requires {@code applied_at} to be set exactly when the
 * status is APPROVED. A payment can never exceed what is still owed. A repeated
 * submit with the same Idempotency-Key returns the first payment.
 *
 * Authorization (who may act on which bill) is checked by the controller.
 */
@Service
public class PaymentService {

    private static final Logger log = LogManager.getLogger(PaymentService.class);

    private final PaymentRepository payments;
    private final TenantBillRepository bills;
    private final StoredFileRepository files;
    private final TenantProfileRepository profiles;
    private final TenantPropertyMembershipRepository memberships;
    private final AccessGuard guard;

    public PaymentService(PaymentRepository payments, TenantBillRepository bills, StoredFileRepository files,
                          TenantProfileRepository profiles, TenantPropertyMembershipRepository memberships,
                          AccessGuard guard) {
        this.payments = payments;
        this.bills = bills;
        this.files = files;
        this.profiles = profiles;
        this.memberships = memberships;
        this.guard = guard;
    }

    /** Owner: money received in cash, bank, wallet… → approved and applied now. */
    @Transactional
    public PaymentResponse record(UUID billId, RecordPaymentRequest req, String idempotencyKey) {
        AuthUser user = guard.requireUser();
        Optional<Payment> replay = replay(user, idempotencyKey);
        if (replay.isPresent()) {
            return response(replay.get());
        }
        validateDate(req.getPaidAtBs());
        TenantBill bill = lockPayableBill(billId);
        requireWithinBalance(bill, req.getAmount());

        Payment p = payments.save(Payment.builder()
                .propertyId(bill.getPropertyId())
                .membershipId(bill.getMembershipId())
                .billId(bill.getId())
                .amount(req.getAmount())
                .method(req.getMethod())
                .source(Source.OWNER_RECORDED)
                .status(Status.APPROVED)
                .paidAtBs(req.getPaidAtBs())
                .reference(req.getReference())
                .note(req.getNote())
                .submittedBy(user.userId())
                .decidedBy(user.userId())
                .decidedAt(Instant.now())
                .appliedAt(Instant.now())
                .idempotencyKey(blankToNull(idempotencyKey))
                .build());
        apply(bill, p.getAmount());
        log.info("Payment recorded — bill: {}, amount: {}, method: {}", billId, p.getAmount(), p.getMethod());
        return PaymentResponse.from(p, state(bill));
    }

    /** Tenant: a receipt for money they paid → pending until the owner decides. */
    @Transactional
    public PaymentResponse submitProof(UUID billId, RecordPaymentRequest req, String idempotencyKey) {
        AuthUser user = guard.requireUser();
        Optional<Payment> replay = replay(user, idempotencyKey);
        if (replay.isPresent()) {
            return response(replay.get());
        }
        validateDate(req.getPaidAtBs());
        if (req.getProofFileId() == null) {
            throw ApiException.badRequest("PROOF_REQUIRED", "Attach a photo or PDF of the payment receipt.");
        }
        StoredFile proof = files.findByIdAndDeletedAtIsNull(req.getProofFileId())
                .filter(f -> f.getOwnerUserId().equals(user.userId()))
                .orElseThrow(() -> ApiException.badRequest("PROOF_INVALID",
                        "The receipt must be a file you uploaded."));
        if (proof.getPurpose() != FilePurpose.PAYMENT_PROOF) {
            throw ApiException.badRequest("PROOF_INVALID", "Upload the receipt as a payment proof.");
        }
        TenantBill bill = lockPayableBill(billId);
        requireWithinBalance(bill, req.getAmount());
        if (payments.existsByBillIdAndStatus(billId, Status.PENDING)) {
            throw ApiException.conflict("PAYMENT_PENDING",
                    "A payment for this bill is already waiting for the owner.");
        }
        Payment p = payments.save(Payment.builder()
                .propertyId(bill.getPropertyId())
                .membershipId(bill.getMembershipId())
                .billId(bill.getId())
                .amount(req.getAmount())
                .method(req.getMethod())
                .source(Source.TENANT_PROOF)
                .status(Status.PENDING)
                .paidAtBs(req.getPaidAtBs())
                .reference(req.getReference())
                .note(req.getNote())
                .proofFileId(proof.getId())
                .submittedBy(user.userId())
                .idempotencyKey(blankToNull(idempotencyKey))
                .build());
        log.info("Payment proof submitted — bill: {}, amount: {}", billId, p.getAmount());
        return PaymentResponse.from(p, state(bill));
    }

    /** Owner accepts a tenant's proof: the bill is reduced, once. */
    @Transactional
    public PaymentResponse approve(UUID paymentId) {
        AuthUser user = guard.requireUser();
        Payment p = lockPending(paymentId);
        TenantBill bill = lockPayableBill(p.getBillId());
        if (p.getAmount().compareTo(bill.getBalanceDue()) > 0) {
            throw ApiException.conflict("EXCEEDS_BALANCE", "This payment is more than the bill still owes ("
                    + bill.getBalanceDue().toPlainString() + "). Reject it and ask the tenant to resend.");
        }
        p.setStatus(Status.APPROVED);
        p.setDecidedBy(user.userId());
        p.setDecidedAt(Instant.now());
        p.setAppliedAt(Instant.now());
        payments.save(p);
        apply(bill, p.getAmount());
        log.info("Payment approved — payment: {}, bill: {}", paymentId, bill.getId());
        return PaymentResponse.from(p, state(bill));
    }

    @Transactional
    public PaymentResponse reject(UUID paymentId, String reason) {
        AuthUser user = guard.requireUser();
        Payment p = lockPending(paymentId);
        p.setStatus(Status.REJECTED);
        p.setDecidedBy(user.userId());
        p.setDecidedAt(Instant.now());
        p.setRejectionReason(reason.trim());
        payments.save(p);
        log.info("Payment rejected — payment: {}", paymentId);
        return response(p);
    }

    /** Tenant withdraws their own proof while it is still pending. */
    @Transactional
    public PaymentResponse cancel(UUID paymentId) {
        Payment p = lockPending(paymentId);
        p.setStatus(Status.CANCELLED);
        payments.save(p);
        return response(p);
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> forBill(UUID billId) {
        return payments.findByBillIdOrderByCreatedAtDesc(billId).stream()
                .map(p -> PaymentResponse.from(p, null)).toList();
    }

    @Transactional(readOnly = true)
    public Page<PaymentResponse> forProperty(UUID propertyId, Status status, Pageable pageable) {
        Page<Payment> page = status == null ? payments.findByPropertyId(propertyId, pageable)
                : payments.findByPropertyIdAndStatus(propertyId, status, pageable);
        return page.map(p -> PaymentResponse.from(p, null));
    }

    /** The signed-in tenant's own payments, newest first. */
    @Transactional(readOnly = true)
    public Page<PaymentResponse> mine(Pageable pageable) {
        AuthUser user = guard.requireUser();
        List<UUID> ids = profiles.findByUserId(user.userId())
                .map(tp -> memberships.findByTenantProfileId(tp.getId(), Pageable.unpaged()).getContent()
                        .stream().map(m -> m.getId()).toList())
                .orElse(List.of());
        if (ids.isEmpty()) {
            return Page.empty(pageable);
        }
        return payments.findByMembershipIdIn(ids, pageable).map(p -> PaymentResponse.from(p, null));
    }

    // ── Rules ───────────────────────────────────────────────────────────────

    private Optional<Payment> replay(AuthUser user, String key) {
        String k = blankToNull(key);
        return k == null ? Optional.empty() : payments.findBySubmittedByAndIdempotencyKey(user.userId(), k);
    }

    private TenantBill lockPayableBill(UUID billId) {
        TenantBill bill = bills.findByIdForUpdate(billId)
                .orElseThrow(() -> ApiException.notFound("BILL_NOT_FOUND", "Bill not found."));
        if (bill.getStatus() != TenantBill.Status.ISSUED || bill.getSupersededByBillId() != null) {
            throw ApiException.badRequest("BILL_NOT_PAYABLE", bill.getStatus() == TenantBill.Status.DRAFT
                    ? "This bill has not been sent yet." : "This bill is no longer active.");
        }
        return bill;
    }

    private Payment lockPending(UUID paymentId) {
        Payment p = payments.findByIdForUpdate(paymentId)
                .orElseThrow(() -> ApiException.notFound("PAYMENT_NOT_FOUND", "Payment not found."));
        if (p.getStatus() != Status.PENDING) {
            throw ApiException.conflict("PAYMENT_ALREADY_DECIDED",
                    "This payment is already " + p.getStatus().name().toLowerCase() + ".");
        }
        return p;
    }

    private static void requireWithinBalance(TenantBill bill, BigDecimal amount) {
        if (bill.getBalanceDue().signum() <= 0) {
            throw ApiException.conflict("BILL_ALREADY_PAID", "This bill is already fully paid.");
        }
        if (amount.compareTo(bill.getBalanceDue()) > 0) {
            throw ApiException.badRequest("AMOUNT_EXCEEDS_BALANCE",
                    "The amount is more than the bill still owes (" + bill.getBalanceDue().toPlainString() + ").");
        }
    }

    /** Adds an approved amount to the bill (the only place a bill's paid amount changes). */
    private void apply(TenantBill bill, BigDecimal amount) {
        BigDecimal paid = bill.getAmountPaid().add(amount);
        BigDecimal balance = bill.getTotalDue().subtract(paid);
        bill.setAmountPaid(paid);
        bill.setBalanceDue(balance.signum() < 0 ? BigDecimal.ZERO : balance);
        bill.setPaymentStatus(balance.signum() <= 0 ? TenantBill.PaymentStatus.PAID
                : TenantBill.PaymentStatus.PARTIAL);
        bills.save(bill);
    }

    private PaymentResponse response(Payment p) {
        return PaymentResponse.from(p, bills.findById(p.getBillId()).map(PaymentService::state).orElse(null));
    }

    private static BillState state(TenantBill b) {
        return new BillState(b.getTotalDue(), b.getAmountPaid(), b.getBalanceDue(), b.getPaymentStatus().name());
    }

    private static void validateDate(String bs) {
        if (!BsCalendar.isValid(bs)) {
            throw ApiException.badRequest("INVALID_DATE", "The payment date is not a valid BS date.");
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
