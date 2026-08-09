package com.renterp.domain.billing.service;

import com.renterp.common.exception.InvalidOperationException;
import com.renterp.domain.property.entity.Property.RoundingMethod;
import com.renterp.domain.property.entity.Property.RoundingRemainderTo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Money splitting + rounding for the billing engine, built so a shared cost always
 * reconciles <em>exactly</em> to the amount that was split — spec Edge Case B9
 * ("rounding leaves a remainder → allocated per config; hard assertion
 * sum(charges) == actual cost before commit").
 *
 * <p>Each tenant's share is rounded per the property's {@link RoundingMethod}; the leftover
 * (total − Σ rounded shares) is handed to a single tenant chosen by the property's
 * {@link RoundingRemainderTo} setting, so the shares sum to the total to the paisa. All
 * arithmetic is {@link BigDecimal} — never floating point.
 */
final class BillingMath {

    private BillingMath() {}

    // Working precision for the un-rounded raw share, before applying the property's method.
    private static final int RAW_SCALE = 6;

    /** The result of splitting a shared total among weighted tenants. */
    record SplitResult(List<BigDecimal> shares, List<BigDecimal> rawShares) {}

    /**
     * Round a single monetary value per the property's rounding method.
     * WHOLE_NUMBER/CEILING/FLOOR collapse to whole rupees; STANDARD/EXACT keep paisa.
     */
    static BigDecimal round(BigDecimal value, RoundingMethod method) {
        BigDecimal rounded = switch (method) {
            case WHOLE_NUMBER -> value.setScale(0, RoundingMode.HALF_UP);
            case CEILING      -> value.setScale(0, RoundingMode.CEILING);
            case FLOOR        -> value.setScale(0, RoundingMode.FLOOR);
            case STANDARD     -> value.setScale(2, RoundingMode.HALF_UP);
            case EXACT        -> value.setScale(2, RoundingMode.HALF_UP); // money is 2dp; "exact" = no coarser rounding
        };
        return rounded.setScale(2, RoundingMode.HALF_UP); // normalise every share to 2dp storage scale
    }

    /**
     * Split {@code total} across tenants in proportion to {@code weights}, rounding each
     * share per {@code method} and allocating the remainder per {@code remainderTo} so the
     * shares sum to {@code total} exactly.
     *
     * @param weights one per tenant, in tenant order; all-zero weights are only valid when
     *                {@code total} is zero (nothing to split).
     */
    static SplitResult split(BigDecimal total, List<BigDecimal> weights,
                             RoundingMethod method, RoundingRemainderTo remainderTo) {
        int n = weights.size();
        if (n == 0) {
            if (total.compareTo(BigDecimal.ZERO) != 0) {
                throw new InvalidOperationException("Cannot split a non-zero cost among zero tenants: " + total);
            }
            return new SplitResult(new ArrayList<>(), new ArrayList<>());
        }

        BigDecimal totalWeight = weights.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        List<BigDecimal> raw = new ArrayList<>(n);
        List<BigDecimal> rounded = new ArrayList<>(n);

        if (totalWeight.compareTo(BigDecimal.ZERO) == 0) {
            // No weight anywhere — only legitimate when there is nothing to split.
            if (total.compareTo(BigDecimal.ZERO) != 0) {
                throw new InvalidOperationException(
                        "Cannot split " + total + " when all tenant weights are zero");
            }
            for (int i = 0; i < n; i++) { raw.add(BigDecimal.ZERO); rounded.add(zero2()); }
            return new SplitResult(rounded, raw);
        }

        for (BigDecimal w : weights) {
            BigDecimal rawShare = total.multiply(w)
                    .divide(totalWeight, RAW_SCALE, RoundingMode.HALF_UP);
            raw.add(rawShare);
        }
        return reconcile(total, raw, method, remainderTo);
    }

    /**
     * Round pre-computed raw shares (which should already sum to {@code total}) per
     * {@code method} and allocate the leftover per {@code remainderTo} so Σ rounded == total
     * exactly (B9). Used by the segment engine, which computes raw shares day-by-day across
     * changing denominators rather than from a single weight vector.
     */
    static SplitResult reconcile(BigDecimal total, List<BigDecimal> rawShares,
                                 RoundingMethod method, RoundingRemainderTo remainderTo) {
        int n = rawShares.size();
        if (n == 0) {
            if (total.compareTo(BigDecimal.ZERO) != 0) {
                throw new InvalidOperationException("Cannot allocate a non-zero cost to zero tenants: " + total);
            }
            return new SplitResult(new ArrayList<>(), new ArrayList<>());
        }
        List<BigDecimal> rounded = new ArrayList<>(n);
        for (BigDecimal rs : rawShares) rounded.add(round(rs, method));

        // Allocate the leftover so Σ rounded == total exactly (B9).
        BigDecimal sumRounded = rounded.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal remainder = total.setScale(2, RoundingMode.HALF_UP).subtract(sumRounded);
        if (remainder.compareTo(BigDecimal.ZERO) != 0) {
            int idx = remainderIndex(rawShares, remainderTo);
            rounded.set(idx, rounded.get(idx).add(remainder).setScale(2, RoundingMode.HALF_UP));
        }

        // Hard assertion — must reconcile to the paisa before the caller commits.
        BigDecimal check = rounded.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (check.compareTo(total.setScale(2, RoundingMode.HALF_UP)) != 0) {
            throw new InvalidOperationException(
                    "Reconciliation failed (B9): shares sum to " + check + " but total is " + total);
        }
        return new SplitResult(rounded, rawShares);
    }

    private static int remainderIndex(List<BigDecimal> rawShares, RoundingRemainderTo to) {
        return switch (to) {
            case FIRST_TENANT -> 0;
            case LAST_TENANT  -> rawShares.size() - 1;
            case HIGHEST_SHARE -> {
                int best = 0;
                for (int i = 1; i < rawShares.size(); i++) {
                    if (rawShares.get(i).compareTo(rawShares.get(best)) > 0) best = i;
                }
                yield best;
            }
        };
    }

    static BigDecimal zero2() {
        return BigDecimal.ZERO.setScale(2, RoundingMode.UNNECESSARY);
    }

    /** Prorate an amount by an occupied/total day fraction, at money (2dp) scale. */
    static BigDecimal prorate(BigDecimal amount, int daysOccupied, int daysInPeriod) {
        if (daysOccupied >= daysInPeriod) return amount.setScale(2, RoundingMode.HALF_UP);
        return amount.multiply(BigDecimal.valueOf(daysOccupied))
                .divide(BigDecimal.valueOf(daysInPeriod), 2, RoundingMode.HALF_UP);
    }
}
