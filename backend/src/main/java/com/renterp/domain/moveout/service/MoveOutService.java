package com.renterp.domain.moveout.service;

import com.renterp.common.exception.ApiException;
import com.renterp.common.util.BsCalendar;
import com.renterp.domain.auth.security.AccessGuard;
import com.renterp.domain.auth.security.AuthUser;
import com.renterp.domain.billing.entity.TenantBill;
import com.renterp.domain.billing.repository.TenantBillRepository;
import com.renterp.domain.moveout.dto.MoveOutNoticeRequest;
import com.renterp.domain.moveout.dto.MoveOutResponse;
import com.renterp.domain.moveout.dto.SettleMoveOutRequest;
import com.renterp.domain.moveout.entity.MoveOut;
import com.renterp.domain.moveout.entity.MoveOut.Status;
import com.renterp.domain.moveout.repository.MoveOutRepository;
import com.renterp.domain.payment.entity.Payment;
import com.renterp.domain.payment.repository.PaymentRepository;
import com.renterp.domain.payment.service.PaymentService;
import com.renterp.domain.property.entity.Property;
import com.renterp.domain.property.repository.PropertyRepository;
import com.renterp.domain.structure.entity.Room;
import com.renterp.domain.structure.repository.RoomRepository;
import com.renterp.domain.tenancy.dto.TerminateMembershipRequest;
import com.renterp.domain.tenancy.entity.RoomAssignment;
import com.renterp.domain.tenancy.entity.TenantPropertyMembership;
import com.renterp.domain.tenancy.entity.TenantPropertyMembership.MembershipStatus;
import com.renterp.domain.tenancy.repository.RoomAssignmentRepository;
import com.renterp.domain.tenancy.repository.TenantProfileRepository;
import com.renterp.domain.tenancy.repository.TenantPropertyMembershipRepository;
import com.renterp.domain.tenancy.service.MembershipService;
import com.renterp.domain.tenantfinance.entity.TenantDeposit;
import com.renterp.domain.tenantfinance.entity.TenantDeposit.DepositStatus;
import com.renterp.domain.tenantfinance.repository.TenantDepositRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Move-out (Phase 7): notice → settlement.
 *
 * <p>Settlement, in one transaction: the held deposit pays the tenant's unpaid
 * bills oldest first (each as an approved payment, so a bill is reduced once
 * and its history shows it), then covers any final charges and deductions the
 * owner enters; what is left is refunded. The tenancy is ended on the
 * move-out date, which closes its room assignments (the rooms become vacant).
 * Bills the deposit could not cover stay owed — history is never deleted.
 * Settlement waits until no payment proof is pending, so nothing is decided
 * twice.
 */
@Service
public class MoveOutService {

    private static final Logger log = LogManager.getLogger(MoveOutService.class);

    private final MoveOutRepository moveOuts;
    private final TenantPropertyMembershipRepository memberships;
    private final TenantProfileRepository profiles;
    private final PropertyRepository properties;
    private final TenantBillRepository bills;
    private final TenantDepositRepository deposits;
    private final PaymentRepository payments;
    private final PaymentService paymentService;
    private final MembershipService membershipService;
    private final RoomAssignmentRepository assignments;
    private final RoomRepository rooms;
    private final AccessGuard guard;

    public MoveOutService(MoveOutRepository moveOuts, TenantPropertyMembershipRepository memberships,
                          TenantProfileRepository profiles, PropertyRepository properties, TenantBillRepository bills,
                          TenantDepositRepository deposits, PaymentRepository payments, PaymentService paymentService,
                          MembershipService membershipService, RoomAssignmentRepository assignments,
                          RoomRepository rooms, AccessGuard guard) {
        this.moveOuts = moveOuts;
        this.memberships = memberships;
        this.profiles = profiles;
        this.properties = properties;
        this.bills = bills;
        this.deposits = deposits;
        this.payments = payments;
        this.paymentService = paymentService;
        this.membershipService = membershipService;
        this.assignments = assignments;
        this.rooms = rooms;
        this.guard = guard;
    }

    @Transactional
    public MoveOutResponse giveNotice(UUID membershipId, MoveOutNoticeRequest req) {
        AuthUser user = guard.requireUser();
        TenantPropertyMembership m = activeMembership(membershipId);
        if (moveOuts.findFirstByMembershipIdAndStatus(membershipId, Status.NOTICE_GIVEN).isPresent()) {
            throw ApiException.conflict("MOVE_OUT_PENDING", "A move-out notice is already open for this tenant.");
        }
        String today = BsCalendar.today();
        requireDate(req.getPlannedMoveOutBs());
        if (req.getPlannedMoveOutBs().compareTo(today) < 0) {
            throw ApiException.badRequest("DATE_IN_PAST", "The move-out date cannot be in the past.");
        }
        Property p = properties.findById(m.getPropertyId()).orElseThrow();
        boolean shortNotice = BsCalendar.daysBetween(today, req.getPlannedMoveOutBs()) < p.getVacancyNoticePeriodDays();
        boolean byTenant = profiles.findById(m.getTenantProfileId())
                .map(tp -> user.userId().equals(tp.getUserId())).orElse(false);
        MoveOut saved = moveOuts.save(MoveOut.builder()
                .membershipId(membershipId)
                .propertyId(m.getPropertyId())
                .status(Status.NOTICE_GIVEN)
                .requestedBy(user.userId())
                .requestedByTenant(byTenant)
                .noticeDateBs(today)
                .plannedMoveOutBs(req.getPlannedMoveOutBs())
                .shortNotice(shortNotice)
                .reason(req.getReason())
                .build());
        log.info("Move-out notice — membership: {}, date: {}, short: {}", membershipId, req.getPlannedMoveOutBs(), shortNotice);
        return response(saved);
    }

    @Transactional(readOnly = true)
    public Optional<MoveOutResponse> latest(UUID membershipId) {
        return moveOuts.findFirstByMembershipIdOrderByCreatedAtDesc(membershipId).map(this::response);
    }

    @Transactional
    public MoveOutResponse cancel(UUID moveOutId) {
        MoveOut mo = lockOpen(moveOutId);
        mo.setStatus(Status.CANCELLED);
        mo.setCancelledAt(Instant.now());
        return response(moveOuts.save(mo));
    }

    @Transactional
    public MoveOutResponse settle(UUID moveOutId, SettleMoveOutRequest req) {
        AuthUser user = guard.requireUser();
        MoveOut mo = lockOpen(moveOutId);
        requireDate(req.getMovedOutBs());
        TenantPropertyMembership m = activeMembership(mo.getMembershipId());
        if (req.getMovedOutBs().compareTo(m.getStartedAtBs()) < 0) {
            throw ApiException.badRequest("INVALID_DATE", "The move-out date is before the tenant moved in.");
        }
        if (payments.existsByMembershipIdAndStatus(m.getId(), Payment.Status.PENDING)) {
            throw ApiException.conflict("PENDING_PAYMENTS",
                    "Approve or reject the tenant's waiting payment before settling.");
        }

        List<TenantBill> unpaid = unpaidBills(m.getId());
        BigDecimal outstanding = sum(unpaid);
        Optional<TenantDeposit> deposit = deposits.findByMembershipId(m.getId())
                .filter(d -> d.getStatus() == DepositStatus.HELD);
        BigDecimal held = deposit.map(TenantDeposit::getAmount).orElse(BigDecimal.ZERO);
        BigDecimal finalCharges = nz(req.getFinalCharges());
        BigDecimal deductions = nz(req.getDeductions());

        // 1. Deposit pays unpaid bills, oldest first.
        BigDecimal left = held;
        for (TenantBill b : unpaid) {
            if (left.signum() <= 0) {
                break;
            }
            BigDecimal a = left.min(b.getBalanceDue());
            paymentService.applyFromDeposit(b.getId(), a, req.getMovedOutBs(), user.userId());
            left = left.subtract(a);
        }
        BigDecimal appliedToBills = held.subtract(left);
        // 2. Then final charges and deductions.
        BigDecimal extra = finalCharges.add(deductions);
        BigDecimal coverExtra = left.min(extra);
        left = left.subtract(coverExtra);
        BigDecimal refund = left;
        BigDecimal stillOwes = outstanding.subtract(appliedToBills).add(extra.subtract(coverExtra));

        deposit.ifPresent(d -> {
            if (held.signum() > 0) {
                d.setStatus(refund.compareTo(held) == 0 ? DepositStatus.REFUNDED_FULL
                        : refund.signum() > 0 ? DepositStatus.REFUNDED_PARTIAL : DepositStatus.APPLIED_TO_BALANCE);
                deposits.save(d);
            }
        });

        // 3. End the tenancy (closes room assignments → rooms vacant).
        TerminateMembershipRequest t = new TerminateMembershipRequest();
        t.setEndedAtBs(req.getMovedOutBs());
        t.setReason("Moved out");
        membershipService.terminate(m.getId(), t);

        mo.setStatus(Status.SETTLED);
        mo.setMovedOutBs(req.getMovedOutBs());
        mo.setOutstandingBefore(outstanding);
        mo.setFinalCharges(finalCharges);
        mo.setFinalChargesNote(req.getFinalChargesNote());
        mo.setDeductions(deductions);
        mo.setDeductionsNote(req.getDeductionsNote());
        mo.setDepositHeld(held);
        mo.setDepositApplied(held.subtract(refund));
        mo.setRefundAmount(refund);
        mo.setTenantStillOwes(stillOwes);
        mo.setSettledBy(user.userId());
        mo.setSettledAt(Instant.now());
        MoveOut saved = moveOuts.save(mo);
        log.info("Move-out settled — membership: {}, refund: {}, still owes: {}", m.getId(), refund, stillOwes);
        return response(saved);
    }

    @Transactional(readOnly = true)
    public List<MoveOutResponse> forProperty(UUID propertyId, Status status) {
        List<MoveOut> list = status == null ? moveOuts.findByPropertyIdOrderByCreatedAtDesc(propertyId)
                : moveOuts.findByPropertyIdAndStatusOrderByPlannedMoveOutBsAsc(propertyId, status);
        return list.stream().map(this::response).toList();
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private MoveOut lockOpen(UUID id) {
        MoveOut mo = moveOuts.findByIdForUpdate(id)
                .orElseThrow(() -> ApiException.notFound("MOVE_OUT_NOT_FOUND", "Move-out not found."));
        if (mo.getStatus() != Status.NOTICE_GIVEN) {
            throw ApiException.conflict("MOVE_OUT_CLOSED", "This move-out is already " + mo.getStatus().name().toLowerCase() + ".");
        }
        return mo;
    }

    private TenantPropertyMembership activeMembership(UUID id) {
        TenantPropertyMembership m = memberships.findById(id)
                .orElseThrow(() -> ApiException.notFound("MEMBERSHIP_NOT_FOUND", "Tenancy not found."));
        if (m.getStatus() != MembershipStatus.ACTIVE) {
            throw ApiException.badRequest("TENANCY_ENDED", "This tenancy has already ended.");
        }
        return m;
    }

    private List<TenantBill> unpaidBills(UUID membershipId) {
        return bills.findByMembershipIdAndStatusNot(membershipId, TenantBill.Status.CANCELLED).stream()
                .filter(b -> b.getStatus() == TenantBill.Status.ISSUED && b.getSupersededByBillId() == null
                        && b.getBalanceDue().signum() > 0)
                .sorted(Comparator.comparing(TenantBill::getBillingMonthBs))
                .toList();
    }

    private MoveOutResponse response(MoveOut mo) {
        String name = memberships.findById(mo.getMembershipId())
                .flatMap(m -> profiles.findById(m.getTenantProfileId()))
                .map(tp -> tp.getFullName()).orElse(null);
        String roomNames = rooms.findAllById(assignments.findByMembershipIdAndEffectiveToBsIsNull(mo.getMembershipId())
                        .stream().map(RoomAssignment::getRoomId).toList())
                .stream().map(Room::getName).sorted().collect(Collectors.joining(", "));
        BigDecimal previewOutstanding = null;
        BigDecimal previewDeposit = null;
        if (mo.getStatus() == Status.NOTICE_GIVEN) {
            previewOutstanding = sum(unpaidBills(mo.getMembershipId()));
            previewDeposit = deposits.findByMembershipId(mo.getMembershipId())
                    .filter(d -> d.getStatus() == DepositStatus.HELD)
                    .map(TenantDeposit::getAmount).orElse(BigDecimal.ZERO);
        }
        return MoveOutResponse.of(mo, name, roomNames.isEmpty() ? null : roomNames, previewOutstanding, previewDeposit);
    }

    private static BigDecimal sum(List<TenantBill> list) {
        return list.stream().map(TenantBill::getBalanceDue).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static void requireDate(String bs) {
        if (!BsCalendar.isValid(bs)) {
            throw ApiException.badRequest("INVALID_DATE", "That is not a valid BS date.");
        }
    }
}
