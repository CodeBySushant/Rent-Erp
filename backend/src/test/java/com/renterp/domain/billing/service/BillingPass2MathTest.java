package com.renterp.domain.billing.service;

import com.renterp.domain.billing.entity.TariffVersion.Slab;
import com.renterp.domain.property.entity.Property.RoundingMethod;
import com.renterp.domain.property.entity.Property.RoundingRemainderTo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the pure pass-2 billing math: NEA slab pricing ({@link ElectricityEngine#applySlabs})
 * and the mid-period segment split ({@link SegmentEngine#splitShared}, T9). Same package so the
 * package-private engine methods are reachable.
 */
class BillingPass2MathTest {

    private static Slab slab(Integer upto, String rate) {
        return new Slab(upto, new BigDecimal(rate));
    }

    @Test
    @DisplayName("applySlabs prices each band at its own rate, open-ended final slab")
    void slabsPriceEachBand() {
        List<Slab> slabs = List.of(slab(20, "8"), slab(null, "12"));
        // 100 units → 20@8 + 80@12 = 160 + 960 = 1120
        assertEquals(0, ElectricityEngine.applySlabs(slabs, new BigDecimal("100")).compareTo(new BigDecimal("1120")));
        // 15 units → all in first band = 120
        assertEquals(0, ElectricityEngine.applySlabs(slabs, new BigDecimal("15")).compareTo(new BigDecimal("120")));
        // 20 units → exactly the first band = 160
        assertEquals(0, ElectricityEngine.applySlabs(slabs, new BigDecimal("20")).compareTo(new BigDecimal("160")));
    }

    @Test
    @DisplayName("applySlabs handles three bands")
    void slabsThreeBands() {
        List<Slab> slabs = List.of(slab(20, "5"), slab(50, "8"), slab(null, "10"));
        // 70 units → 20@5 + 30@8 + 20@10 = 100 + 240 + 200 = 540
        assertEquals(0, ElectricityEngine.applySlabs(slabs, new BigDecimal("70")).compareTo(new BigDecimal("540")));
    }

    @Test
    @DisplayName("T9: mid-period joiner pays only post-join days; pre-join days fall to the earlier group")
    void segmentSplitMidMonthJoin() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        // 31-day period, total 3100 → daily 100. A present all 31; B joins day 16 (index 15).
        List<SegmentEngine.MemberWindow> windows = List.of(
                new SegmentEngine.MemberWindow(a, 0, 30, BigDecimal.ONE),
                new SegmentEngine.MemberWindow(b, 15, 30, BigDecimal.ONE));
        BillingMath.SplitResult sr = SegmentEngine.splitShared(
                new BigDecimal("3100"), 31, windows, RoundingMethod.STANDARD, RoundingRemainderTo.HIGHEST_SHARE);
        // Days 1-15 solo A = 1500; days 16-31 (16d) split 50/50 → +800 each. A=2300, B=800.
        assertEquals(0, sr.shares().get(0).compareTo(new BigDecimal("2300.00")), "A share");
        assertEquals(0, sr.shares().get(1).compareTo(new BigDecimal("800.00")), "B share");
        BigDecimal sum = sr.shares().get(0).add(sr.shares().get(1));
        assertEquals(0, sum.compareTo(new BigDecimal("3100.00")), "reconciles exactly (B9)");
    }

    @Test
    @DisplayName("T9 segments: joiner gets a MID_MONTH_JOIN row; the present group splits at the join")
    void segmentRowsMidMonthJoin() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        List<SegmentEngine.MemberWindow> windows = List.of(
                new SegmentEngine.MemberWindow(a, 0, 30, BigDecimal.ONE),
                new SegmentEngine.MemberWindow(b, 15, 30, BigDecimal.ONE));
        List<SegmentEngine.SegmentRow> rows = SegmentEngine.buildSegments(31, windows);
        // A splits into denom-1 and denom-2 sub-periods (2 rows); B has 1 row. Total 3.
        assertEquals(3, rows.size());
        long joinRows = rows.stream()
                .filter(r -> r.membershipId().equals(b))
                .filter(r -> r.reason() == com.renterp.domain.billing.entity.BillingRunSegment.Reason.MID_MONTH_JOIN)
                .count();
        assertEquals(1, joinRows, "B has a MID_MONTH_JOIN segment");
        long aDenom1 = rows.stream().filter(r -> r.membershipId().equals(a)).filter(r -> r.denominatorCount() == 1).count();
        assertTrue(aDenom1 >= 1, "A has a pre-join denominator-1 segment");
    }

    @Test
    @DisplayName("splitShared reconciles exactly for weighted (room-weighted) shares")
    void segmentSplitWeighted() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        // Whole period, weights 3:1, total 1000 → 750 / 250.
        List<SegmentEngine.MemberWindow> windows = List.of(
                new SegmentEngine.MemberWindow(a, 0, 29, new BigDecimal("3")),
                new SegmentEngine.MemberWindow(b, 0, 29, BigDecimal.ONE));
        BillingMath.SplitResult sr = SegmentEngine.splitShared(
                new BigDecimal("1000"), 30, windows, RoundingMethod.STANDARD, RoundingRemainderTo.HIGHEST_SHARE);
        assertEquals(0, sr.shares().get(0).compareTo(new BigDecimal("750.00")));
        assertEquals(0, sr.shares().get(1).compareTo(new BigDecimal("250.00")));
    }
}
