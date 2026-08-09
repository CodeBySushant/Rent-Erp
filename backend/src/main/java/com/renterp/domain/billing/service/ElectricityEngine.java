package com.renterp.domain.billing.service;

import com.renterp.common.exception.InvalidOperationException;
import com.renterp.common.exception.ResourceNotFoundException;
import com.renterp.domain.billing.dto.CreateBillingRunRequest;
import com.renterp.domain.billing.entity.TariffVersion;
import com.renterp.domain.billing.repository.TariffVersionRepository;
import com.renterp.domain.meter.entity.Meter;
import com.renterp.domain.meter.entity.Meter.MeterType;
import com.renterp.domain.meter.entity.MeterRoomCoverage;
import com.renterp.domain.meter.repository.MeterRepository;
import com.renterp.domain.meter.repository.MeterRoomCoverageRepository;
import com.renterp.domain.meterreading.entity.MeterReading;
import com.renterp.domain.meterreading.repository.MeterReadingRepository;
import com.renterp.domain.property.entity.Property;
import com.renterp.domain.property.entity.Property.NeaTariffMode;
import com.renterp.domain.property.entity.Property.OverageAction;
import com.renterp.domain.property.entity.Property.RoundingMethod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * SUB_METERED electricity (Billing pass 2). Computes per-tenant electricity from confirmed
 * meter readings — units × rate — where the rate is either the landlord's flat per-unit
 * price (FLAT_RATE) or the NEA blended rate derived from the main meter and the tariff
 * slab table (BLENDED_RATE, §6.3 / B-series NEA compliance). Also computes the M17 overage
 * comparison (Σ sub-meters vs main meter).
 *
 * <p>Consumption window is half-open {@code (periodStartBs, periodEndBs]}: the opening reading
 * dated on the period start (its consumption belongs to the prior period) is excluded; the
 * closing reading dated on the period end is included. Units for a meter = Σ of the
 * confirmed, consumption-bearing readings in that window (deltas already handle rollover per
 * reading — M1).
 *
 * <p>Payer resolution: a sub-meter's rooms as of the period end (M9 coverage-as-of-date) map
 * to billable memberships via {@code roomToMembership}. A covered room with no billable
 * tenant (vacant — M14) is never billed; its units still count toward the overage total.
 */
@Service
public class ElectricityEngine {

    private static final Logger log = LogManager.getLogger(ElectricityEngine.class);
    private static final int RATE_SCALE = 4;

    private final MeterRepository meterRepository;
    private final MeterReadingRepository readingRepository;
    private final MeterRoomCoverageRepository coverageRepository;
    private final TariffVersionRepository tariffRepository;

    public ElectricityEngine(MeterRepository meterRepository,
                             MeterReadingRepository readingRepository,
                             MeterRoomCoverageRepository coverageRepository,
                             TariffVersionRepository tariffRepository) {
        this.meterRepository = meterRepository;
        this.readingRepository = readingRepository;
        this.coverageRepository = coverageRepository;
        this.tariffRepository = tariffRepository;
    }

    /** Full NEA reconciliation snapshot for a BLENDED_RATE run (null for FLAT_RATE). */
    public record NeaSnapshot(UUID tariffVersionId, BigDecimal totalUnits, BigDecimal energyCost,
                              BigDecimal demandCharge, BigDecimal vatAmount, BigDecimal totalBill,
                              BigDecimal blendedRate) {}

    /** M17 overage comparison. commonUnits = max(0, main − Σsub). */
    public record OverageOutcome(BigDecimal mainUnits, BigDecimal subUnitsTotal, BigDecimal commonUnits,
                                 BigDecimal thresholdPercent, boolean overThreshold) {}

    public record SubMeteredResult(Map<UUID, BigDecimal> amountByMembership,
                                   Map<UUID, String> noteByMembership,
                                   NeaSnapshot nea,
                                   OverageOutcome overage) {}

    /**
     * @param roomToMembership roomId → billable membershipId (vacant/absent rooms omitted)
     */
    public SubMeteredResult computeSubMetered(Property property, CreateBillingRunRequest req,
                                              Map<UUID, UUID> roomToMembership, RoundingMethod method) {
        String fromBs = req.getPeriodStartBs();
        String toBs = req.getPeriodEndBs();

        List<Meter> elec = meterRepository.findByPropertyIdAndMeterPurposeAndActiveTrue(
                property.getId(), Meter.MeterPurpose.ELECTRICITY);
        List<Meter> subs = elec.stream().filter(m -> m.getMeterType() == MeterType.TENANT_SUPPLY).toList();
        Meter main = elec.stream().filter(m -> m.getMeterType() == MeterType.MAIN).findFirst().orElse(null);

        if (subs.isEmpty()) {
            throw new InvalidOperationException(
                    "SUB_METERED electricity requires at least one active TENANT_SUPPLY meter");
        }

        // Rate + (for blended) the NEA snapshot.
        BigDecimal rate;
        NeaSnapshot nea = null;
        BigDecimal mainUnits = main != null ? unitsInWindow(main.getId(), fromBs, toBs) : null;

        if (property.getNeaTariffMode() == NeaTariffMode.BLENDED_RATE) {
            if (main == null) {
                throw new InvalidOperationException(
                        "BLENDED_RATE requires an active MAIN electricity meter to measure total units");
            }
            if (mainUnits.compareTo(BigDecimal.ZERO) <= 0) {
                throw new InvalidOperationException(
                        "BLENDED_RATE: no confirmed main-meter consumption in " + fromBs + ".." + toBs
                                + " — cannot derive a blended rate (enter/confirm the main reading first)");
            }
            nea = blended(toBs, mainUnits);
            rate = nea.blendedRate();
        } else {
            rate = require(req.getElectricityRatePerUnit(),
                    "electricityRatePerUnit is required for SUB_METERED FLAT_RATE electricity");
        }

        // Per sub-meter: units → amount → attribute to covered membership(s).
        Map<UUID, BigDecimal> amountByMembership = new HashMap<>();
        Map<UUID, String> noteByMembership = new HashMap<>();
        BigDecimal subUnitsTotal = BigDecimal.ZERO;

        for (Meter sub : subs) {
            BigDecimal units = unitsInWindow(sub.getId(), fromBs, toBs);
            subUnitsTotal = subUnitsTotal.add(units);

            // Rooms this meter served as of the period end (M9), mapped to billable tenants.
            List<UUID> payerRooms = new ArrayList<>();
            for (MeterRoomCoverage cov : coverageRepository.findActiveAsOf(sub.getId(), toBs)) {
                if (roomToMembership.containsKey(cov.getRoomId())) {
                    payerRooms.add(cov.getRoomId());
                }
            }

            boolean hasReading = readingRepository
                    .findConsumptionInWindow(sub.getId(), fromBs, toBs).size() > 0;
            if (payerRooms.isEmpty()) {
                // Vacant/landlord meter — counted in overage total, billed to nobody (M14).
                continue;
            }

            BigDecimal amount = round(units.multiply(rate), method);

            // One membership commonly; if a meter spans rooms of several tenants, split by
            // covered-room count and reconcile the paisa to the highest share.
            Map<UUID, Integer> roomsPerMember = new HashMap<>();
            for (UUID roomId : payerRooms) {
                roomsPerMember.merge(roomToMembership.get(roomId), 1, Integer::sum);
            }
            List<Map.Entry<UUID, Integer>> members = new ArrayList<>(roomsPerMember.entrySet());
            if (members.size() == 1) {
                addAmount(amountByMembership, members.get(0).getKey(), amount);
            } else {
                List<BigDecimal> weights = members.stream()
                        .map(e -> BigDecimal.valueOf(e.getValue())).toList();
                BillingMath.SplitResult sr = BillingMath.split(amount, weights, method,
                        property.getRoundingRemainderTo());
                for (int i = 0; i < members.size(); i++) {
                    addAmount(amountByMembership, members.get(i).getKey(), sr.shares().get(i));
                }
            }
            if (!hasReading) {
                for (Map.Entry<UUID, Integer> e : members) {
                    noteByMembership.put(e.getKey(), "electricity reading pending (B3)");
                }
            }
        }

        // Common units (M17) + optional allocation to tenants at the blended rate.
        OverageOutcome overage = null;
        if (main != null) {
            BigDecimal commonUnits = mainUnits.subtract(subUnitsTotal).max(BigDecimal.ZERO);
            BigDecimal thresholdPct = property.getOverageThresholdPercent();
            BigDecimal allowed = mainUnits.multiply(BigDecimal.ONE.add(thresholdPct.movePointLeft(2)));
            boolean over = subUnitsTotal.compareTo(allowed) > 0;
            overage = new OverageOutcome(mainUnits, subUnitsTotal, commonUnits, thresholdPct, over);

            if (over) {
                log.warn("M17 overage — property: {}, subUnits: {} > allowed: {} (main {} + {}%)",
                        property.getId(), subUnitsTotal, allowed, mainUnits, thresholdPct);
                if (property.getOverageAction() == OverageAction.BLOCK) {
                    throw new InvalidOperationException(
                            "M17 overage: sub-meter units " + subUnitsTotal + " exceed main-meter "
                                    + mainUnits + " + " + thresholdPct + "% threshold; overage action = BLOCK");
                }
            }

            // Blended mode with common-units-charged: allocate the common units to tenants at
            // the blended rate so Σ(billed electricity) reconciles to the full NEA bill (B9).
            if (nea != null && property.isCommonUnitsChargedToTenants()
                    && commonUnits.compareTo(BigDecimal.ZERO) > 0 && !amountByMembership.isEmpty()) {
                BigDecimal commonCost = round(commonUnits.multiply(rate), method);
                List<UUID> members = new ArrayList<>(amountByMembership.keySet());
                List<BigDecimal> ones = members.stream().map(m -> BigDecimal.ONE).toList();
                BillingMath.SplitResult sr = BillingMath.split(commonCost, ones, method,
                        property.getRoundingRemainderTo());
                for (int i = 0; i < members.size(); i++) {
                    addAmount(amountByMembership, members.get(i), sr.shares().get(i));
                    noteByMembership.merge(members.get(i), "incl. common-area units",
                            (a, b) -> a + "; " + b);
                }
            }
        }

        return new SubMeteredResult(amountByMembership, noteByMembership, nea, overage);
    }

    // ── NEA blended rate ───────────────────────────────────────────────────────

    private NeaSnapshot blended(String asOfBs, BigDecimal totalUnits) {
        List<TariffVersion> hits = tariffRepository.findEffectiveOn(asOfBs, PageRequest.of(0, 1));
        if (hits.isEmpty()) {
            throw new ResourceNotFoundException("TariffVersion", "effectiveOn", asOfBs);
        }
        TariffVersion t = hits.get(0);

        BigDecimal energyCost = applySlabs(t.getSlabs(), totalUnits);
        BigDecimal preVat = energyCost.add(t.getDemandCharge()).add(t.getServiceCharge());
        if (preVat.compareTo(t.getMinimumCharge()) < 0) {
            preVat = t.getMinimumCharge();
        }
        BigDecimal vat = preVat.multiply(t.getVatPercent()).movePointLeft(2).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalBill = preVat.add(vat).setScale(2, RoundingMode.HALF_UP);
        BigDecimal blendedRate = totalBill.divide(totalUnits, RATE_SCALE, RoundingMode.HALF_UP);

        return new NeaSnapshot(t.getId(), totalUnits.setScale(2, RoundingMode.HALF_UP),
                energyCost.setScale(2, RoundingMode.HALF_UP), t.getDemandCharge(),
                vat, totalBill, blendedRate);
    }

    /**
     * Apply an ordered NEA slab table to a unit total. Each slab's {@code uptoUnits} is the
     * cumulative inclusive upper bound; the final slab has {@code uptoUnits == null} (open-ended).
     * Units falling in each band are charged at that band's {@code ratePerUnit}.
     */
    static BigDecimal applySlabs(List<TariffVersion.Slab> slabs, BigDecimal units) {
        if (slabs == null || slabs.isEmpty()) {
            throw new InvalidOperationException(
                    "BLENDED_RATE: the effective tariff version has no slabs configured");
        }
        BigDecimal cost = BigDecimal.ZERO;
        BigDecimal lower = BigDecimal.ZERO;      // cumulative units already priced
        BigDecimal remaining = units;
        for (TariffVersion.Slab slab : slabs) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
            BigDecimal band;
            if (slab.getUptoUnits() == null) {
                band = remaining;                // open-ended final slab
            } else {
                BigDecimal upto = BigDecimal.valueOf(slab.getUptoUnits());
                band = upto.subtract(lower).min(remaining).max(BigDecimal.ZERO);
                lower = upto;
            }
            cost = cost.add(band.multiply(slab.getRatePerUnit()));
            remaining = remaining.subtract(band);
        }
        // Units beyond the last bounded slab with no open-ended slab: charge at the last rate.
        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            cost = cost.add(remaining.multiply(slabs.get(slabs.size() - 1).getRatePerUnit()));
        }
        return cost;
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private BigDecimal unitsInWindow(UUID meterId, String fromBs, String toBs) {
        return readingRepository.findConsumptionInWindow(meterId, fromBs, toBs).stream()
                .map(MeterReading::getConsumption)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static void addAmount(Map<UUID, BigDecimal> map, UUID key, BigDecimal amt) {
        map.merge(key, amt, BigDecimal::add);
    }

    private static BigDecimal round(BigDecimal value, RoundingMethod method) {
        return BillingMath.round(value, method);
    }

    private static BigDecimal require(BigDecimal value, String message) {
        if (value == null) throw new InvalidOperationException(message);
        return value;
    }
}
