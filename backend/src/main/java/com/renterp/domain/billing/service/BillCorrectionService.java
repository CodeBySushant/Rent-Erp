package com.renterp.domain.billing.service;

import com.renterp.common.exception.InvalidOperationException;
import com.renterp.common.exception.ResourceNotFoundException;
import com.renterp.domain.billing.dto.BillCorrectionResponse;
import com.renterp.domain.billing.dto.BillLineItem;
import com.renterp.domain.billing.dto.CorrectBillRequest;
import com.renterp.domain.billing.entity.BillCorrection;
import com.renterp.domain.billing.entity.BillingRun;
import com.renterp.domain.billing.entity.TenantBill;
import com.renterp.domain.billing.entity.TenantBillAdjustment;
import com.renterp.domain.billing.repository.BillCorrectionRepository;
import com.renterp.domain.billing.repository.BillingRunRepository;
import com.renterp.domain.billing.repository.TenantBillAdjustmentRepository;
import com.renterp.domain.billing.repository.TenantBillRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Bill corrections (B8). A confirmed bill is never edited in place:
 *
 * <ul>
 *   <li><b>Unpaid</b> → CANCEL_REGENERATE: the original is cancelled and a replacement bill
 *       with the corrected total is issued in the same run, linked via the supersedes chain.</li>
 *   <li><b>Paid / partial</b> → NEXT_BILL_ADJUSTMENT: the original stands; the delta becomes a
 *       PENDING {@code tenant_bill_adjustments} row (CHARGE if the tenant owes more, CREDIT if
 *       less) that the engine carries onto the next bill (P9 mechanism).</li>
 * </ul>
 *
 * Every correction writes an audit row to {@code bill_corrections}.
 */
@Service
public class BillCorrectionService {

    private static final Logger log = LogManager.getLogger(BillCorrectionService.class);

    private final TenantBillRepository billRepository;
    private final TenantBillAdjustmentRepository adjustmentRepository;
    private final BillCorrectionRepository correctionRepository;
    private final BillingRunRepository billingRunRepository;

    public BillCorrectionService(TenantBillRepository billRepository,
                                 TenantBillAdjustmentRepository adjustmentRepository,
                                 BillCorrectionRepository correctionRepository,
                                 BillingRunRepository billingRunRepository) {
        this.billRepository = billRepository;
        this.adjustmentRepository = adjustmentRepository;
        this.correctionRepository = correctionRepository;
        this.billingRunRepository = billingRunRepository;
    }

    @Transactional
    public BillCorrectionResponse correct(UUID billId, CorrectBillRequest req) {
        TenantBill orig = billRepository.findById(billId)
                .orElseThrow(() -> new ResourceNotFoundException("TenantBill", "id", billId));

        if (orig.getStatus() != TenantBill.Status.ISSUED) {
            throw new InvalidOperationException(
                    "Only an ISSUED bill can be corrected (this bill is " + orig.getStatus()
                            + "); a DRAFT run is fixed by cancelling and regenerating the run");
        }

        BigDecimal delta = resolveDelta(orig, req);
        if (delta.compareTo(BigDecimal.ZERO) == 0) {
            throw new InvalidOperationException("Correction delta is zero — nothing to correct");
        }
        BigDecimal newTotal = orig.getTotalDue().add(delta).setScale(2, RoundingMode.HALF_UP);

        boolean unpaid = orig.getPaymentStatus() == TenantBill.PaymentStatus.UNPAID
                && orig.getAmountPaid().compareTo(BigDecimal.ZERO) == 0;

        return unpaid
                ? cancelAndRegenerate(orig, delta, newTotal, req)
                : nextBillAdjustment(orig, delta, req);
    }

    // ── Unpaid → cancel + regenerate ───────────────────────────────────────────

    private BillCorrectionResponse cancelAndRegenerate(TenantBill orig, BigDecimal delta,
                                                       BigDecimal newTotal, CorrectBillRequest req) {
        // Cancel the original FIRST (and flush) so it leaves the one-live-bill-per-run slot
        // free — otherwise the replacement insert collides with uq_tenant_bill_run_membership_live.
        orig.setStatus(TenantBill.Status.CANCELLED);
        billRepository.saveAndFlush(orig);

        List<BillLineItem> items = new ArrayList<>(orig.getLineItems());
        items.add(BillLineItem.of("Correction", "ADJUSTMENT", delta, req.getReason()));

        TenantBill replacement = TenantBill.builder()
                .billingRunId(orig.getBillingRunId())
                .propertyId(orig.getPropertyId())
                .membershipId(orig.getMembershipId())
                .billingMonthBs(orig.getBillingMonthBs())
                .periodStartBs(orig.getPeriodStartBs())
                .periodEndBs(orig.getPeriodEndBs())
                .daysOccupied(orig.getDaysOccupied())
                .daysInPeriod(orig.getDaysInPeriod())
                .prorated(orig.isProrated())
                .rentAmount(orig.getRentAmount())
                .electricityAmount(orig.getElectricityAmount())
                .waterAmount(orig.getWaterAmount())
                .chargesAmount(orig.getChargesAmount())
                .penaltyAmount(orig.getPenaltyAmount())
                .adjustmentsAmount(orig.getAdjustmentsAmount().add(delta))   // keeps totalDue invariant
                .advanceAppliedAmount(orig.getAdvanceAppliedAmount())
                .previousBalance(orig.getPreviousBalance())
                .subtotal(orig.getSubtotal())
                .tdsAmount(orig.getTdsAmount())
                .roundingAdjustment(orig.getRoundingAdjustment())
                .totalDue(newTotal)
                .amountPaid(BillingMath.zero2())
                .balanceDue(newTotal)
                .paymentStatus(TenantBill.PaymentStatus.UNPAID)
                .lineItems(items)
                .status(TenantBill.Status.ISSUED)
                .generatedAtBs(orig.getGeneratedAtBs())
                .gracePeriodDays(orig.getGracePeriodDays())
                .dueDateBs(orig.getDueDateBs())
                .supersedesBillId(orig.getId())
                .notes("Correction of bill " + orig.getId() + ": " + req.getReason())
                .build();
        replacement = billRepository.saveAndFlush(replacement);

        orig.setSupersededByBillId(replacement.getId());
        billRepository.save(orig);

        adjustRunTotal(orig.getBillingRunId(), delta);

        BillCorrection correction = correctionRepository.save(BillCorrection.builder()
                .originalBillId(orig.getId())
                .membershipId(orig.getMembershipId())
                .correctionType(BillCorrection.CorrectionType.CANCEL_REGENERATE)
                .regeneratedBillId(replacement.getId())
                .reason(req.getReason())
                .correctedBy(req.getCorrectedBy())
                .build());

        log.info("Bill corrected (cancel+regenerate) — original: {}, replacement: {}, delta: {}",
                orig.getId(), replacement.getId(), delta);
        return BillCorrectionResponse.from(correction);
    }

    // ── Paid / partial → next-bill adjustment ──────────────────────────────────

    private BillCorrectionResponse nextBillAdjustment(TenantBill orig, BigDecimal delta, CorrectBillRequest req) {
        TenantBillAdjustment adj = adjustmentRepository.save(TenantBillAdjustment.builder()
                .membershipId(orig.getMembershipId())
                .propertyId(orig.getPropertyId())
                .adjustmentType(delta.compareTo(BigDecimal.ZERO) > 0
                        ? TenantBillAdjustment.AdjustmentType.CHARGE
                        : TenantBillAdjustment.AdjustmentType.CREDIT)
                .source(TenantBillAdjustment.Source.BILL_CORRECTION)
                .amount(delta.abs().setScale(2, RoundingMode.HALF_UP))
                .reason(req.getReason())
                .originBillId(orig.getId())
                .status(TenantBillAdjustment.Status.PENDING)
                .createdBy(req.getCorrectedBy())
                .build());

        BillCorrection correction = correctionRepository.save(BillCorrection.builder()
                .originalBillId(orig.getId())
                .membershipId(orig.getMembershipId())
                .correctionType(BillCorrection.CorrectionType.NEXT_BILL_ADJUSTMENT)
                .adjustmentId(adj.getId())
                .reason(req.getReason())
                .correctedBy(req.getCorrectedBy())
                .build());

        log.info("Bill corrected (next-bill adjustment) — original: {}, adjustment: {}, delta: {}",
                orig.getId(), adj.getId(), delta);
        return BillCorrectionResponse.from(correction);
    }

    // ── helpers ─────────────────────────────────────────────────────────────────

    private BigDecimal resolveDelta(TenantBill orig, CorrectBillRequest req) {
        if (req.getCorrectedTotalDue() != null) {
            return req.getCorrectedTotalDue().subtract(orig.getTotalDue()).setScale(2, RoundingMode.HALF_UP);
        }
        if (req.getDeltaAmount() != null) {
            return req.getDeltaAmount().setScale(2, RoundingMode.HALF_UP);
        }
        throw new InvalidOperationException("Provide either correctedTotalDue or deltaAmount");
    }

    private void adjustRunTotal(UUID runId, BigDecimal delta) {
        BillingRun run = billingRunRepository.findById(runId).orElse(null);
        if (run != null) {
            run.setTotalBilled(run.getTotalBilled().add(delta).setScale(2, RoundingMode.HALF_UP));
            billingRunRepository.save(run);
        }
    }
}
