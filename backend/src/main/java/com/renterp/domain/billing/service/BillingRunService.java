package com.renterp.domain.billing.service;

import com.renterp.domain.notification.entity.Notification;
import com.renterp.domain.notification.service.NotificationService;
import com.renterp.common.exception.DuplicateResourceException;
import com.renterp.common.exception.InvalidOperationException;
import com.renterp.common.exception.ResourceNotFoundException;
import com.renterp.common.util.BsCalendar;
import com.renterp.domain.billing.dto.*;
import com.renterp.domain.billing.entity.*;
import com.renterp.domain.billing.entity.BillingRun.Status;
import com.renterp.domain.billing.entity.BillingRun.TariffMode;
import com.renterp.domain.billing.repository.*;
import com.renterp.domain.charge.entity.ChargeTemplate;
import com.renterp.domain.charge.repository.ChargeTemplateRepository;
import com.renterp.domain.property.entity.Property;
import com.renterp.domain.property.entity.Property.*;
import com.renterp.domain.property.repository.PropertyRepository;
import com.renterp.domain.tenancy.entity.RoomAssignment;
import com.renterp.domain.tenancy.entity.TenantPropertyMembership;
import com.renterp.domain.tenancy.entity.TenantPropertyMembership.MembershipStatus;
import com.renterp.domain.tenancy.repository.RoomAssignmentRepository;
import com.renterp.domain.tenancy.repository.TenantPropertyMembershipRepository;
import com.renterp.domain.tenantfinance.entity.TenantAdvanceRent;
import com.renterp.domain.tenantfinance.entity.TenantOpeningBalance;
import com.renterp.domain.tenantfinance.repository.TenantAdvanceRentRepository;
import com.renterp.domain.tenantfinance.repository.TenantOpeningBalanceRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The billing engine (Phase 5).
 *
 * <p><b>Pass 1</b> wired the non-metered subset. <b>Pass 2</b> adds SUB_METERED electricity and
 * the NEA blended rate ({@link ElectricityEngine}), the mid-period shared-denominator segment
 * engine ({@link SegmentEngine}, T9/M9), KUKL/boring water, CUSTOM split, the M17 overage
 * comparison, and the B15 async worker path ({@link BillingAsyncService}). Confirmation still
 * enforces oldest-first (B13) and a hard reconciliation guard (B9); cancellation supports the
 * cancel+regenerate correction path (B8) and releases consumed adjustments.
 *
 * <p>Penalty accrual stays 0 here (deferred to the Payment phase, which owns paid/overdue
 * state — see DEVLOG_BILLING rule #10).
 */
@Service
public class BillingRunService {

    private static final Logger log = LogManager.getLogger(BillingRunService.class);

    // B15 — runs with more than this many billable tenants go async unless the caller overrides.
    static final int ASYNC_TENANT_THRESHOLD = 25;

    private final NotificationService notifier;
    private final PropertyRepository propertyRepository;
    private final BillingRunRepository billingRunRepository;
    private final TenantBillRepository tenantBillRepository;
    private final BillingRunSegmentRepository segmentRepository;
    private final TenantBillAdjustmentRepository adjustmentRepository;
    private final TenantPropertyMembershipRepository membershipRepository;
    private final RoomAssignmentRepository assignmentRepository;
    private final ChargeTemplateRepository chargeRepository;
    private final TenantOpeningBalanceRepository openingBalanceRepository;
    private final TenantAdvanceRentRepository advanceRepository;
    private final BillingRunProgressRepository progressRepository;
    private final ElectricityEngine electricityEngine;
    private final BillingAsyncService asyncService;

    public BillingRunService(PropertyRepository propertyRepository,
                             BillingRunRepository billingRunRepository,
                             TenantBillRepository tenantBillRepository,
                             BillingRunSegmentRepository segmentRepository,
                             TenantBillAdjustmentRepository adjustmentRepository,
                             TenantPropertyMembershipRepository membershipRepository,
                             RoomAssignmentRepository assignmentRepository,
                             ChargeTemplateRepository chargeRepository,
                             TenantOpeningBalanceRepository openingBalanceRepository,
                             TenantAdvanceRentRepository advanceRepository,
                             BillingRunProgressRepository progressRepository,
                             ElectricityEngine electricityEngine,
                             BillingAsyncService asyncService,
                             NotificationService notifier) {
        this.propertyRepository = propertyRepository;
        this.billingRunRepository = billingRunRepository;
        this.tenantBillRepository = tenantBillRepository;
        this.segmentRepository = segmentRepository;
        this.adjustmentRepository = adjustmentRepository;
        this.membershipRepository = membershipRepository;
        this.assignmentRepository = assignmentRepository;
        this.chargeRepository = chargeRepository;
        this.openingBalanceRepository = openingBalanceRepository;
        this.advanceRepository = advanceRepository;
        this.progressRepository = progressRepository;
        this.electricityEngine = electricityEngine;
        this.asyncService = asyncService;
        this.notifier = notifier;
    }

    // Internal per-membership working record while building the run.
    record BillContext(TenantPropertyMembership membership, String occStart, String occEnd,
                       int daysOccupied, int startDay, int endDay, BigDecimal rent,
                       int roomWeight, List<UUID> roomIds, BigDecimal splitWeight) {}

    // ── Generate ─────────────────────────────────────────────────────────────

    @Transactional
    public BillingRunResponse generate(UUID propertyId, CreateBillingRunRequest req) {
        // Validate dates up front (throws → 400).
        BsCalendar.parse(req.getPeriodStartBs());
        BsCalendar.parse(req.getPeriodEndBs());
        BsCalendar.parse(req.getGeneratedAtBs());
        if (BsCalendar.daysBetween(req.getPeriodStartBs(), req.getPeriodEndBs()) < 0) {
            throw new InvalidOperationException(
                    "periodEndBs " + req.getPeriodEndBs() + " precedes periodStartBs " + req.getPeriodStartBs());
        }

        // Concurrency layer 2 (B10): serialise run creation per property on the property row.
        Property property = propertyRepository.findByIdForUpdate(propertyId)
                .orElseThrow(() -> new ResourceNotFoundException("Property", "id", propertyId));

        // Concurrency layer 1 (B10): idempotent replay.
        if (req.getIdempotencyKey() != null) {
            Optional<BillingRun> replay =
                    billingRunRepository.findByPropertyIdAndIdempotencyKey(propertyId, req.getIdempotencyKey());
            if (replay.isPresent()) {
                log.info("Idempotent replay of billing run — property: {}, key: {}", propertyId, req.getIdempotencyKey());
                return BillingRunResponse.from(replay.get());
            }
        }

        // One live run per period (B10 backstop; the unique index is the hard guarantee).
        billingRunRepository
                .findByPropertyIdAndBillingMonthBsAndStatusNot(propertyId, req.getBillingMonthBs(), Status.CANCELLED)
                .ifPresent(r -> {
                    throw new DuplicateResourceException("BillingRun", "billingMonthBs", req.getBillingMonthBs());
                });

        int daysInPeriod = (int) BsCalendar.inclusiveDays(req.getPeriodStartBs(), req.getPeriodEndBs());

        // Create the run first so bills/segments have an FK target and the unique index fires.
        BillingRun run = BillingRun.builder()
                .propertyId(propertyId)
                .billingMonthBs(req.getBillingMonthBs())
                .periodStartBs(req.getPeriodStartBs())
                .periodEndBs(req.getPeriodEndBs())
                .status(Status.DRAFT)
                .tariffMode(property.getNeaTariffMode() == NeaTariffMode.BLENDED_RATE
                        ? TariffMode.BLENDED_RATE : TariffMode.FLAT_RATE)
                .generatedAtBs(req.getGeneratedAtBs())
                .idempotencyKey(req.getIdempotencyKey())
                .notes(req.getNotes())
                .build();
        run = billingRunRepository.saveAndFlush(run);

        // Billable set: active memberships whose occupancy overlaps the period.
        List<BillContext> billable = buildBillableSet(property, req, daysInPeriod);

        // B11 — no tenants: record an empty run so the timeline has no gap.
        if (billable.isEmpty()) {
            run.setTenantCount(0);
            run.setTotalBilled(BillingMath.zero2());
            run = billingRunRepository.saveAndFlush(run);
            log.info("Empty billing run recorded (B11) — property: {}, period: {}", propertyId, req.getBillingMonthBs());
            return BillingRunResponse.from(run);
        }

        // B15 — large runs go async: persist a RUNNING progress row and hand off to a worker.
        boolean async = req.getAsync() != null ? req.getAsync() : billable.size() > ASYNC_TENANT_THRESHOLD;
        if (async) {
            run.setAsync(true);
            run.setTenantCount(billable.size());
            run = billingRunRepository.saveAndFlush(run);
            progressRepository.save(BillingRunProgress.builder()
                    .billingRunId(run.getId())
                    .totalTenants(billable.size())
                    .status(BillingRunProgress.Status.RUNNING)
                    .build());
            asyncService.enqueue(run.getId(), propertyId, req);
            log.info("Billing run enqueued async (B15) — id: {}, tenants: {}", run.getId(), billable.size());
            return BillingRunResponse.from(run);
        }

        // Synchronous build.
        BigDecimal totalBilled = buildBills(run, property, req, billable, daysInPeriod, null);
        run.setTenantCount(billable.size());
        run.setTotalBilled(totalBilled);
        run = billingRunRepository.saveAndFlush(run);

        log.info("Billing run generated (DRAFT) — property: {}, period: {}, tenants: {}, total: {}",
                propertyId, req.getBillingMonthBs(), billable.size(), totalBilled);
        return BillingRunResponse.from(run);
    }

    /**
     * Build every tenant bill for a run and return the run total. Shared by the synchronous
     * path and the async worker; when {@code progress} is non-null it is incremented per bill.
     * Populates NEA reconciliation + segment rows as a side effect on {@code run}.
     */
    @Transactional
    public BigDecimal buildBills(BillingRun run, Property property, CreateBillingRunRequest req,
                                 List<BillContext> billable, int daysInPeriod, BillingRunProgress progress) {
        UUID propertyId = property.getId();
        int n = billable.size();
        RoundingMethod method = property.getRoundingMethod();
        RoundingRemainderTo remainderTo = property.getRoundingRemainderTo();

        // roomId → billable membershipId (for SUB_METERED payer resolution).
        Map<UUID, UUID> roomToMembership = new HashMap<>();
        for (BillContext ctx : billable) {
            for (UUID roomId : ctx.roomIds()) roomToMembership.put(roomId, ctx.membership().getId());
        }

        // Segment windows (day-indexed) with each member's split weight (T9/M9).
        List<SegmentEngine.MemberWindow> windows = new ArrayList<>(n);
        for (BillContext ctx : billable) {
            windows.add(new SegmentEngine.MemberWindow(
                    ctx.membership().getId(), ctx.startDay(), ctx.endDay(), ctx.splitWeight()));
        }

        // ── Electricity ────────────────────────────────────────────────────────
        ElectricityBillingMode elecMode = property.getElectricityBillingMode();
        Map<UUID, BigDecimal> elecByMember = new HashMap<>();
        Map<UUID, String> elecNote = new HashMap<>();
        if (elecMode == ElectricityBillingMode.MAIN_METER_ONLY) {
            BigDecimal total = require(req.getElectricityTotalAmount(),
                    "electricityTotalAmount is required for MAIN_METER_ONLY electricity");
            BillingMath.SplitResult sr = SegmentEngine.splitShared(total, daysInPeriod, windows, method, remainderTo);
            for (int i = 0; i < n; i++) elecByMember.put(windows.get(i).membershipId(), sr.shares().get(i));
        } else if (elecMode == ElectricityBillingMode.SUB_METERED) {
            ElectricityEngine.SubMeteredResult r =
                    electricityEngine.computeSubMetered(property, req, roomToMembership, method);
            elecByMember.putAll(r.amountByMembership());
            elecNote.putAll(r.noteByMembership());
            if (r.nea() != null) applyNea(run, r.nea());
        }
        // FIXED_PER_TENANT / INCLUDED_IN_RENT handled per-bill below.

        // ── Water ────────────────────────────────────────────────────────────────
        WaterMode waterMode = property.getWaterMode();
        Map<UUID, BigDecimal> waterByMember = new HashMap<>();
        BigDecimal sharedWater = BigDecimal.ZERO;
        if (waterMode == WaterMode.KUKL_SPLIT || waterMode == WaterMode.KUKL_AND_BORING) {
            sharedWater = sharedWater.add(require(req.getWaterKuklAmount(),
                    "waterKuklAmount is required for KUKL water modes"));
        }
        if (waterMode == WaterMode.BORING_PUMP_ONLY || waterMode == WaterMode.KUKL_AND_BORING) {
            sharedWater = sharedWater.add(require(req.getWaterBoringAmount(),
                    "waterBoringAmount is required for boring-pump water modes"));
        }
        if (sharedWater.compareTo(BigDecimal.ZERO) > 0) {
            BillingMath.SplitResult sr = SegmentEngine.splitShared(sharedWater, daysInPeriod, windows, method, remainderTo);
            for (int i = 0; i < n; i++) waterByMember.put(windows.get(i).membershipId(), sr.shares().get(i));
        }
        if (waterMode == WaterMode.FIXED_PER_TENANT) {
            require(req.getWaterFixedAmount(), "waterFixedAmount is required for FIXED_PER_TENANT water");
        }

        // ── Charge templates ──────────────────────────────────────────────────────
        // Active charges only: SHARED split segment-aware; FIXED_PER_TENANT prorated per bill.
        // B7 (deactivated charges): an inactive charge is not billed here; a THIS_CYCLE_PRORATED
        // deactivation is realised as a one-time P9 adjustment at deactivation time, which the
        // engine consumes below — so proration lands without the engine over-billing forever.
        List<ChargeTemplate> charges = chargeRepository.findByPropertyIdAndActiveTrue(propertyId);
        Map<UUID, Map<UUID, BigDecimal>> sharedChargeByMember = new LinkedHashMap<>();
        for (ChargeTemplate c : charges) {
            if (c.getSplitBasis() == ChargeTemplate.SplitBasis.SHARED) {
                BillingMath.SplitResult sr = SegmentEngine.splitShared(c.getAmount(), daysInPeriod, windows, method, remainderTo);
                Map<UUID, BigDecimal> byMember = new HashMap<>();
                for (int i = 0; i < n; i++) byMember.put(windows.get(i).membershipId(), sr.shares().get(i));
                sharedChargeByMember.put(c.getId(), byMember);
            }
        }

        // ── Build each bill ─────────────────────────────────────────────────────
        BigDecimal totalBilled = BigDecimal.ZERO;
        for (BillContext ctx : billable) {
            TenantPropertyMembership m = ctx.membership();
            UUID mid = m.getId();
            boolean prorated = ctx.daysOccupied() < daysInPeriod;
            List<BillLineItem> items = new ArrayList<>();

            // Rent (prorated by occupancy).
            BigDecimal rentAmt = BillingMath.prorate(ctx.rent(), ctx.daysOccupied(), daysInPeriod);
            items.add(new BillLineItem("Rent", "RENT", null, null, rentAmt,
                    prorated ? "prorated " + ctx.daysOccupied() + "/" + daysInPeriod + " days" : null));

            // Electricity.
            BigDecimal elecAmt = switch (elecMode) {
                case INCLUDED_IN_RENT -> BillingMath.zero2();
                case FIXED_PER_TENANT -> BillingMath.prorate(require(req.getElectricityFixedAmount(),
                        "electricityFixedAmount is required for FIXED_PER_TENANT electricity"),
                        ctx.daysOccupied(), daysInPeriod);
                case MAIN_METER_ONLY, SUB_METERED -> elecByMember.getOrDefault(mid, BillingMath.zero2());
            };
            if (elecMode != ElectricityBillingMode.INCLUDED_IN_RENT) {
                String note = switch (elecMode) {
                    case MAIN_METER_ONLY -> "main-meter share ("
                            + (property.getDefaultSplitRule() == SplitRule.ROOM_WEIGHTED ? "room-weighted"
                            : property.getDefaultSplitRule() == SplitRule.CUSTOM ? "custom" : "equal") + ")";
                    case SUB_METERED -> elecNote.getOrDefault(mid, "sub-meter"
                            + (run.getTariffMode() == TariffMode.BLENDED_RATE ? " @ blended rate" : ""));
                    case FIXED_PER_TENANT -> prorated ? "prorated " + ctx.daysOccupied() + "/" + daysInPeriod : null;
                    default -> null;
                };
                items.add(new BillLineItem("Electricity", "ELECTRICITY", null, null, elecAmt, note));
            }

            // Water.
            BigDecimal waterAmt = switch (waterMode) {
                case INCLUDED_IN_RENT -> BillingMath.zero2();
                case FIXED_PER_TENANT -> BillingMath.prorate(req.getWaterFixedAmount(), ctx.daysOccupied(), daysInPeriod);
                case KUKL_SPLIT, BORING_PUMP_ONLY, KUKL_AND_BORING -> waterByMember.getOrDefault(mid, BillingMath.zero2());
            };
            if (waterMode != WaterMode.INCLUDED_IN_RENT) {
                String note = switch (waterMode) {
                    case FIXED_PER_TENANT -> prorated ? "prorated " + ctx.daysOccupied() + "/" + daysInPeriod : null;
                    case KUKL_SPLIT -> "KUKL split";
                    case BORING_PUMP_ONLY -> "boring pump split";
                    case KUKL_AND_BORING -> "KUKL + boring split";
                    default -> null;
                };
                items.add(new BillLineItem("Water", "WATER", null, null, waterAmt, note));
            }

            // Charge templates.
            BigDecimal chargesAmt = BillingMath.zero2();
            for (ChargeTemplate c : charges) {
                BigDecimal amt;
                String note;
                if (c.getSplitBasis() == ChargeTemplate.SplitBasis.SHARED) {
                    amt = sharedChargeByMember.get(c.getId()).getOrDefault(mid, BillingMath.zero2());
                    note = "shared split";
                } else {
                    amt = BillingMath.prorate(c.getAmount(), ctx.daysOccupied(), daysInPeriod);
                    note = prorated ? "prorated " + ctx.daysOccupied() + "/" + daysInPeriod : null;
                }
                chargesAmt = chargesAmt.add(amt);
                items.add(new BillLineItem(c.getName(), "CHARGE", null, null, amt, note));
            }

            // Subtotal (penalty is 0 — accrual belongs to the Payment phase).
            BigDecimal subtotal = rentAmt.add(elecAmt).add(waterAmt).add(chargesAmt);

            // Previous balance (T7) — opening balance applied on the first bill only.
            BigDecimal previousBalance = BillingMath.zero2();
            if (tenantBillRepository.countByMembershipIdAndStatusNot(mid, TenantBill.Status.CANCELLED) == 0) {
                Optional<TenantOpeningBalance> ob = openingBalanceRepository.findByMembershipId(mid);
                if (ob.isPresent()) {
                    TenantOpeningBalance o = ob.get();
                    previousBalance = o.getDirection() == TenantOpeningBalance.Direction.OWED_BY_TENANT
                            ? o.getAmount() : o.getAmount().negate();
                    items.add(new BillLineItem("Previous balance", "PREVIOUS_BALANCE", null, null,
                            previousBalance, "opening balance carried from onboarding (T7)"));
                }
            }

            // Adjustments (P9 one-time carry-forward) — apply and consume PENDING rows.
            List<TenantBillAdjustment> pending =
                    adjustmentRepository.findByMembershipIdAndStatus(mid, TenantBillAdjustment.Status.PENDING);
            BigDecimal adjNet = BillingMath.zero2();
            for (TenantBillAdjustment a : pending) {
                BigDecimal signed = a.getAdjustmentType() == TenantBillAdjustment.AdjustmentType.CHARGE
                        ? a.getAmount() : a.getAmount().negate();
                adjNet = adjNet.add(signed);
                items.add(new BillLineItem(
                        a.getReason() != null ? a.getReason() : "Adjustment", "ADJUSTMENT",
                        null, null, signed, a.getSource().name()));
            }

            // TDS deduction (tenant withholds tax on rent).
            BigDecimal tdsAmt = BillingMath.zero2();
            if (property.isTdsEnabled() && property.getTdsRatePercent() != null) {
                tdsAmt = BillingMath.round(
                        rentAmt.multiply(property.getTdsRatePercent()).divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP),
                        method);
                items.add(new BillLineItem("TDS", "TDS", null, null, tdsAmt.negate(),
                        property.getTdsRatePercent() + "% of rent, withheld"));
            }

            // Advance rent (T8) — consume available advance against rent, shown as a credit.
            BigDecimal available = advanceAvailable(mid);
            BigDecimal advanceApplied = rentAmt.min(available.max(BigDecimal.ZERO)).setScale(2, RoundingMode.HALF_UP);
            if (advanceApplied.compareTo(BigDecimal.ZERO) > 0) {
                items.add(new BillLineItem("Advance rent applied", "ADVANCE", null, null,
                        advanceApplied.negate(), "prepaid rent consumed (T8)"));
            }

            // Total due. Component amounts are already the final reconciled shares (B9 remainder
            // is baked into each share by the split), so it is NOT re-added here.
            BigDecimal totalDue = subtotal
                    .subtract(tdsAmt)
                    .add(previousBalance)
                    .add(adjNet)
                    .subtract(advanceApplied)
                    .setScale(2, RoundingMode.HALF_UP);

            String dueDate = BsCalendar.addDays(req.getGeneratedAtBs(), property.getGracePeriodDays());

            TenantBill bill = TenantBill.builder()
                    .billingRunId(run.getId())
                    .propertyId(propertyId)
                    .membershipId(mid)
                    .billingMonthBs(req.getBillingMonthBs())
                    .periodStartBs(req.getPeriodStartBs())
                    .periodEndBs(req.getPeriodEndBs())
                    .daysOccupied(ctx.daysOccupied())
                    .daysInPeriod(daysInPeriod)
                    .prorated(prorated)
                    .rentAmount(rentAmt)
                    .electricityAmount(elecAmt)
                    .waterAmount(waterAmt)
                    .chargesAmount(chargesAmt)
                    .penaltyAmount(BillingMath.zero2())
                    .adjustmentsAmount(adjNet)
                    .advanceAppliedAmount(advanceApplied)
                    .previousBalance(previousBalance)
                    .subtotal(subtotal)
                    .tdsAmount(tdsAmt)
                    .roundingAdjustment(BillingMath.zero2())
                    .totalDue(totalDue)
                    .amountPaid(BillingMath.zero2())
                    .balanceDue(totalDue)
                    .paymentStatus(TenantBill.PaymentStatus.UNPAID)
                    .lineItems(items)
                    .status(TenantBill.Status.DRAFT)
                    .generatedAtBs(req.getGeneratedAtBs())
                    .gracePeriodDays(property.getGracePeriodDays())
                    .dueDateBs(dueDate)
                    .build();
            bill = tenantBillRepository.saveAndFlush(bill);

            // Consume the adjustments this bill applied.
            for (TenantBillAdjustment a : pending) {
                a.setStatus(TenantBillAdjustment.Status.APPLIED);
                a.setAppliedBillId(bill.getId());
                adjustmentRepository.save(a);
            }

            totalBilled = totalBilled.add(totalDue);
            if (progress != null) {
                progress.setProcessedTenants(progress.getProcessedTenants() + 1);
                progressRepository.save(progress);
            }
        }

        // ── Segment rows (T9/M9 audit) ──────────────────────────────────────────
        for (SegmentEngine.SegmentRow sr : SegmentEngine.buildSegments(daysInPeriod, windows)) {
            segmentRepository.save(BillingRunSegment.builder()
                    .billingRunId(run.getId())
                    .membershipId(sr.membershipId())
                    .segmentStartBs(BsCalendar.addDays(req.getPeriodStartBs(), sr.startDay()))
                    .segmentEndBs(BsCalendar.addDays(req.getPeriodStartBs(), sr.endDay()))
                    .days(sr.days())
                    .denominatorCount(sr.denominatorCount())
                    .reason(sr.reason())
                    .build());
        }

        return totalBilled.setScale(2, RoundingMode.HALF_UP);
    }

    /** Build the billable set: active memberships whose occupancy overlaps the period. */
    @Transactional
    public List<BillContext> buildBillableSet(Property property, CreateBillingRunRequest req, int daysInPeriod) {
        SplitRule splitRule = property.getDefaultSplitRule();
        List<BillContext> billable = new ArrayList<>();
        for (TenantPropertyMembership m :
                membershipRepository.findByPropertyIdAndStatus(property.getId(), MembershipStatus.ACTIVE)) {
            String occStart = laterOf(req.getPeriodStartBs(), m.getStartedAtBs());
            String occEnd = req.getPeriodEndBs();
            if (m.getEndedAtBs() != null && BsCalendar.daysBetween(m.getEndedAtBs(), occEnd) > 0) {
                occEnd = m.getEndedAtBs();   // defensive; ACTIVE memberships normally have no end
            }
            if (BsCalendar.daysBetween(occStart, occEnd) < 0) {
                continue;   // membership not active during this period
            }
            int daysOcc = (int) BsCalendar.inclusiveDays(occStart, occEnd);
            int startDay = (int) BsCalendar.daysBetween(req.getPeriodStartBs(), occStart);
            int endDay = startDay + daysOcc - 1;

            List<RoomAssignment> assignments = assignmentRepository.findByMembershipIdAndEffectiveToBsIsNull(m.getId());
            BigDecimal rent = assignments.stream()
                    .map(RoomAssignment::getMonthlyRent)
                    .filter(java.util.Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            List<UUID> roomIds = assignments.stream().map(RoomAssignment::getRoomId).toList();

            BigDecimal weight = switch (splitRule) {
                case EQUAL -> BigDecimal.ONE;
                case ROOM_WEIGHTED -> BigDecimal.valueOf(Math.max(1, assignments.size()));
                case CUSTOM -> {
                    if (req.getCustomWeights() == null || !req.getCustomWeights().containsKey(m.getId())) {
                        throw new InvalidOperationException(
                                "CUSTOM split requires a weight for every billable membership; missing: " + m.getId());
                    }
                    yield req.getCustomWeights().get(m.getId());
                }
            };
            billable.add(new BillContext(m, occStart, occEnd, daysOcc, startDay, endDay,
                    rent, assignments.size(), roomIds, weight));
        }
        return billable;
    }

    /**
     * Async worker entry point (B15). Proxied call from {@link BillingAsyncService} so it runs
     * in its own transaction; the internal buildBillableSet/buildBills self-calls join it.
     */
    @Transactional
    public void buildAsync(UUID runId, UUID propertyId, CreateBillingRunRequest req) {
        BillingRun run = requireRun(runId);
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new ResourceNotFoundException("Property", "id", propertyId));
        BillingRunProgress progress = progressRepository.findByBillingRunId(runId)
                .orElseThrow(() -> new ResourceNotFoundException("BillingRunProgress", "billingRunId", runId));
        int daysInPeriod = (int) BsCalendar.inclusiveDays(req.getPeriodStartBs(), req.getPeriodEndBs());
        List<BillContext> billable = buildBillableSet(property, req, daysInPeriod);
        BigDecimal total = buildBills(run, property, req, billable, daysInPeriod, progress);
        run.setTenantCount(billable.size());
        run.setTotalBilled(total);
        billingRunRepository.save(run);
        progress.setStatus(BillingRunProgress.Status.COMPLETED);
        progressRepository.save(progress);
    }

    /** Record an async build failure on its progress row (separate tx from the rolled-back build). */
    @Transactional
    public void markAsyncFailed(UUID runId, String error) {
        progressRepository.findByBillingRunId(runId).ifPresent(p -> {
            p.setStatus(BillingRunProgress.Status.FAILED);
            String msg = error == null ? "unknown error" : (error.length() > 4000 ? error.substring(0, 4000) : error);
            p.setLastError(msg);
            progressRepository.save(p);
        });
    }

    private void applyNea(BillingRun run, ElectricityEngine.NeaSnapshot nea) {
        run.setTariffVersionId(nea.tariffVersionId());
        run.setNeaTotalUnits(nea.totalUnits());
        run.setNeaEnergyCost(nea.energyCost());
        run.setNeaDemandCharge(nea.demandCharge());
        run.setNeaVatAmount(nea.vatAmount());
        run.setNeaTotalBill(nea.totalBill());
        run.setNeaBlendedRate(nea.blendedRate());
    }

    // ── Confirm ──────────────────────────────────────────────────────────────

    @Transactional
    public BillingRunResponse confirm(UUID runId, ConfirmBillingRunRequest req) {
        BillingRun run = requireRun(runId);
        if (run.getStatus() == Status.CONFIRMED) {
            throw new InvalidOperationException("Billing run " + runId + " is already CONFIRMED");
        }
        if (run.getStatus() == Status.CANCELLED) {
            throw new InvalidOperationException("Billing run " + runId + " is CANCELLED and cannot be confirmed");
        }
        // An async run still being built cannot be confirmed until its worker finishes (B15).
        if (run.isAsync()) {
            BillingRunProgress p = progressRepository.findByBillingRunId(runId).orElse(null);
            if (p != null && p.getStatus() != BillingRunProgress.Status.COMPLETED) {
                throw new InvalidOperationException(
                        "Billing run " + runId + " is still generating (" + (p.getStatus()) + "); cannot confirm yet");
            }
        }

        // B13 — an earlier-period draft must be confirmed first.
        for (BillingRun d : billingRunRepository.findByPropertyIdAndStatus(run.getPropertyId(), Status.DRAFT)) {
            if (!d.getId().equals(run.getId()) && d.getBillingMonthBs().compareTo(run.getBillingMonthBs()) < 0) {
                throw new InvalidOperationException(
                        "Confirm the earlier draft run for " + d.getBillingMonthBs() + " first (B13, oldest-first)");
            }
        }

        // Reconciliation commit guard (B9) — bill totals must equal the run total.
        List<TenantBill> bills = tenantBillRepository.findByBillingRunId(runId).stream()
                .filter(b -> b.getStatus() != TenantBill.Status.CANCELLED)
                .toList();
        BigDecimal sum = bills.stream().map(TenantBill::getTotalDue)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        if (sum.compareTo(run.getTotalBilled().setScale(2, RoundingMode.HALF_UP)) != 0) {
            throw new InvalidOperationException(
                    "Reconciliation failed at confirm (B9): bills sum to " + sum + " but run total is " + run.getTotalBilled());
        }

        for (TenantBill b : bills) {
            b.setStatus(TenantBill.Status.ISSUED);
            tenantBillRepository.save(b);
        }
        run.setStatus(Status.CONFIRMED);
        run.setConfirmedAt(Instant.now());
        run.setConfirmedBy(req != null ? req.getConfirmedBy() : null);
        run = billingRunRepository.saveAndFlush(run);
        log.info("Billing run confirmed — id: {}, bills issued: {}", runId, bills.size());
        for (TenantBill b : bills) {
            notifier.toTenant(b.getMembershipId(), Notification.Type.BILL_ISSUED,
                    b.getBillingMonthBs() + " · " + NotificationService.rs(b.getTotalDue())
                            + (b.getDueDateBs() == null ? "" : " · due " + b.getDueDateBs()),
                    "BILL", b.getId());
        }
        return BillingRunResponse.from(run);
    }

    // ── Cancel ───────────────────────────────────────────────────────────────

    @Transactional
    public BillingRunResponse cancel(UUID runId, CancelBillingRunRequest req) {
        BillingRun run = requireRun(runId);
        if (run.getStatus() == Status.CANCELLED) {
            throw new InvalidOperationException("Billing run " + runId + " is already CANCELLED");
        }
        // DRAFT or CONFIRMED may be cancelled — a confirmed-but-unpaid run is cancelled then
        // regenerated (B8). Payment-state gating (only-if-unpaid) arrives with the Payment phase.
        List<TenantBill> bills = tenantBillRepository.findByBillingRunId(runId).stream()
                .filter(b -> b.getStatus() != TenantBill.Status.CANCELLED)
                .toList();
        for (TenantBill b : bills) {
            b.setStatus(TenantBill.Status.CANCELLED);
            tenantBillRepository.save(b);
            // Release any adjustments this bill consumed, back to PENDING for the next run.
            for (TenantBillAdjustment a : adjustmentRepository.findByAppliedBillId(b.getId())) {
                if (a.getStatus() == TenantBillAdjustment.Status.APPLIED) {
                    a.setStatus(TenantBillAdjustment.Status.PENDING);
                    a.setAppliedBillId(null);
                    adjustmentRepository.save(a);
                }
            }
        }
        run.setStatus(Status.CANCELLED);
        run.setCancelledAt(Instant.now());
        run.setCancelledBy(req != null ? req.getCancelledBy() : null);
        run.setCancellationReason(req != null ? req.getReason() : null);
        run = billingRunRepository.saveAndFlush(run);
        log.info("Billing run cancelled — id: {}, bills cancelled: {}", runId, bills.size());
        return BillingRunResponse.from(run);
    }

    // ── Reads ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public BillingRunResponse getRun(UUID runId) {
        return BillingRunResponse.from(requireRun(runId));
    }

    @Transactional(readOnly = true)
    public Page<BillingRunResponse> listRuns(UUID propertyId, Pageable pageable) {
        return billingRunRepository.findByPropertyId(propertyId, pageable).map(BillingRunResponse::from);
    }

    @Transactional(readOnly = true)
    public List<BillingRunSegmentResponse> getSegments(UUID runId) {
        requireRun(runId);
        return segmentRepository.findByBillingRunId(runId).stream()
                .map(BillingRunSegmentResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public BillingRunProgressResponse getProgress(UUID runId) {
        requireRun(runId);
        BillingRunProgress p = progressRepository.findByBillingRunId(runId)
                .orElseThrow(() -> new ResourceNotFoundException("BillingRunProgress", "billingRunId", runId));
        return BillingRunProgressResponse.from(p);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    // Advance available = Σ active HELD advance amounts − Σ advance already applied on the
    // membership's non-cancelled bills. Ledger-based, so a cancelled bill's consumption
    // returns to the pool automatically.
    private BigDecimal advanceAvailable(UUID membershipId) {
        BigDecimal held = advanceRepository.findByMembershipIdOrderByCoveredFromBsAsc(membershipId).stream()
                .filter(TenantAdvanceRent::isActive)
                .filter(a -> a.getStatus() == TenantAdvanceRent.AdvanceStatus.HELD)
                .map(TenantAdvanceRent::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal alreadyApplied = tenantBillRepository
                .findByMembershipIdAndStatusNot(membershipId, TenantBill.Status.CANCELLED).stream()
                .map(TenantBill::getAdvanceAppliedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return held.subtract(alreadyApplied);
    }

    private static String laterOf(String a, String b) {
        return BsCalendar.daysBetween(a, b) >= 0 ? b : a;   // b if b >= a else a
    }

    private static BigDecimal require(BigDecimal value, String message) {
        if (value == null) throw new InvalidOperationException(message);
        return value;
    }

    private BillingRun requireRun(UUID id) {
        return billingRunRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("BillingRun", "id", id));
    }
}
