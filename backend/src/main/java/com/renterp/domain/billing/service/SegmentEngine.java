package com.renterp.domain.billing.service;

import com.renterp.domain.billing.entity.BillingRunSegment.Reason;
import com.renterp.domain.property.entity.Property.RoundingMethod;
import com.renterp.domain.property.entity.Property.RoundingRemainderTo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Segment-based splitting of shared costs across a period whose set of billable tenants
 * changes mid-period (T9 — shared-charge denominator changes on join/exit; M9 — meter
 * coverage-as-of-date). Spec rule: "every mid-period event creates a boundary with its own
 * denominator; Σ(tenant charges) equals actual cost exactly."
 *
 * <p>Model: a shared monthly total is spread evenly across the period's days; each day's slice
 * is split among the tenants present that day, weighted per the split rule. A tenant who joins
 * on day 16 therefore pays nothing for days 1–15, and days 1–15 are split among the smaller
 * pre-join group — exactly the T9 semantics — without any explicit segment bookkeeping in the
 * hot path. Days with nobody present are absorbed by the landlord (vacant days never billed).
 *
 * <p>Pure functions, no state — mirrors {@link BillingMath}. Day indices are 0-based within the
 * period ([0, daysInPeriod−1]).
 */
final class SegmentEngine {

    private SegmentEngine() {}

    private static final int RAW_SCALE = 8;

    /** A member's continuous occupancy within the period, with their split weight. */
    record MemberWindow(UUID membershipId, int startDay, int endDay, BigDecimal weight) {}

    /** A per-membership audit segment (→ one billing_run_segments row). */
    record SegmentRow(UUID membershipId, int startDay, int endDay, int days,
                      int denominatorCount, Reason reason) {}

    /**
     * Split {@code total} across members by presence-day and weight, reconciled to the paisa.
     * Returns shares aligned to {@code members} order. Cost for days nobody is present is not
     * charged to tenants (landlord-absorbed), so Σ shares may be less than {@code total}.
     */
    static BillingMath.SplitResult splitShared(BigDecimal total, int daysInPeriod,
                                               List<MemberWindow> members,
                                               RoundingMethod method, RoundingRemainderTo remainderTo) {
        int m = members.size();
        BigDecimal[] raw = new BigDecimal[m];
        for (int i = 0; i < m; i++) raw[i] = BigDecimal.ZERO;

        if (m == 0 || total.compareTo(BigDecimal.ZERO) == 0) {
            List<BigDecimal> zeros = new ArrayList<>(m);
            for (int i = 0; i < m; i++) zeros.add(BillingMath.zero2());
            return new BillingMath.SplitResult(zeros, java.util.Arrays.asList(raw));
        }

        BigDecimal dailyCost = total.divide(BigDecimal.valueOf(daysInPeriod), RAW_SCALE, RoundingMode.HALF_UP);
        int allocatedDays = 0;

        for (int d = 0; d < daysInPeriod; d++) {
            // Members present on day d and the day's denominator (Σ their weights).
            BigDecimal denom = BigDecimal.ZERO;
            for (MemberWindow w : members) {
                if (d >= w.startDay() && d <= w.endDay()) denom = denom.add(w.weight());
            }
            if (denom.compareTo(BigDecimal.ZERO) <= 0) continue;   // vacant day → landlord absorbs
            allocatedDays++;
            for (int i = 0; i < m; i++) {
                MemberWindow w = members.get(i);
                if (d >= w.startDay() && d <= w.endDay()) {
                    raw[i] = raw[i].add(dailyCost.multiply(w.weight())
                            .divide(denom, RAW_SCALE, RoundingMode.HALF_UP));
                }
            }
        }

        // Reconcile to the amount actually allocated (whole-period occupancy → == total).
        BigDecimal allocated = dailyCost.multiply(BigDecimal.valueOf(allocatedDays))
                .setScale(2, RoundingMode.HALF_UP);
        return BillingMath.reconcile(allocated, java.util.Arrays.asList(raw), method, remainderTo);
    }

    /**
     * Build the per-membership audit segments: split each member's occupancy window at the
     * points where the total present-denominator (headcount of overlapping members) changes,
     * one row per continuous sub-period. reason = MID_MONTH_JOIN when the member starts after
     * the period start, MID_MONTH_EXIT when they end before the period end, else FULL_PERIOD.
     */
    static List<SegmentRow> buildSegments(int daysInPeriod, List<MemberWindow> members) {
        List<SegmentRow> rows = new ArrayList<>();
        for (MemberWindow w : members) {
            int segStart = w.startDay();
            int prevDenom = headcountOn(members, w.startDay());
            for (int d = w.startDay() + 1; d <= w.endDay(); d++) {
                int denom = headcountOn(members, d);
                if (denom != prevDenom) {
                    rows.add(row(w, daysInPeriod, segStart, d - 1, prevDenom));
                    segStart = d;
                    prevDenom = denom;
                }
            }
            rows.add(row(w, daysInPeriod, segStart, w.endDay(), prevDenom));
        }
        return rows;
    }

    private static int headcountOn(List<MemberWindow> members, int day) {
        int c = 0;
        for (MemberWindow w : members) if (day >= w.startDay() && day <= w.endDay()) c++;
        return c;
    }

    private static SegmentRow row(MemberWindow w, int daysInPeriod, int start, int end, int denom) {
        Reason reason;
        if (start == w.startDay() && w.startDay() > 0) reason = Reason.MID_MONTH_JOIN;
        else if (end == w.endDay() && w.endDay() < daysInPeriod - 1) reason = Reason.MID_MONTH_EXIT;
        else reason = Reason.FULL_PERIOD;
        return new SegmentRow(w.membershipId(), start, end, end - start + 1, denom, reason);
    }
}
