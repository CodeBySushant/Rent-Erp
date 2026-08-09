package com.renterp.domain.meterreading.repository;

import com.renterp.domain.meterreading.entity.MeterReading;
import com.renterp.domain.meterreading.entity.MeterReading.ReadingStatus;
import com.renterp.domain.meterreading.entity.MeterReading.ReadingType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MeterReadingRepository extends JpaRepository<MeterReading, UUID> {

    Page<MeterReading> findByMeterId(UUID meterId, Pageable pageable);

    Page<MeterReading> findByMeterIdAndReadingType(UUID meterId, ReadingType type, Pageable pageable);

    Page<MeterReading> findByMeterIdAndStatus(UUID meterId, ReadingStatus status, Pageable pageable);

    // Latest CONFIRMED reading for a meter, ordered by reading_date_bs (VARCHAR compares
    // lexicographically, correct for zero-padded YYYY-MM-DD BS dates). Ties break on
    // createdAt so a later submission for the same physical date still wins deterministically.
    @Query("""
        SELECT r FROM MeterReading r
        WHERE r.meterId = :meterId
          AND r.status = com.renterp.domain.meterreading.entity.MeterReading$ReadingStatus.CONFIRMED
        ORDER BY r.readingDateBs DESC, r.createdAt DESC
    """)
    List<MeterReading> findLatestConfirmedForMeter(@Param("meterId") UUID meterId, Pageable pageable);

    default Optional<MeterReading> findLatestConfirmed(UUID meterId) {
        List<MeterReading> hits = findLatestConfirmedForMeter(meterId, org.springframework.data.domain.PageRequest.of(0, 1));
        return hits.isEmpty() ? Optional.empty() : Optional.of(hits.get(0));
    }

    // Rolling window used by the M5 estimation helper — last N CONFIRMED consumption-
    // bearing readings for a meter (excludes chain-anchor rows where consumption is NULL).
    @Query("""
        SELECT r FROM MeterReading r
        WHERE r.meterId = :meterId
          AND r.status = com.renterp.domain.meterreading.entity.MeterReading$ReadingStatus.CONFIRMED
          AND r.consumption IS NOT NULL
        ORDER BY r.readingDateBs DESC
    """)
    List<MeterReading> findRecentConsumption(@Param("meterId") UUID meterId, Pageable pageable);

    // Billing engine (pass 2): CONFIRMED, consumption-bearing readings for a meter whose
    // reading_date_bs falls in the half-open window (fromBs, toBs]. The lower bound is
    // EXCLUSIVE so the period's opening reading (dated on periodStart, whose consumption
    // belongs to the PRIOR period) is not double-counted; the upper bound is INCLUSIVE so
    // the closing reading (dated on/at period end, or the generation date for a chased
    // reading — B4) is captured. Σ consumption over this set = the meter's units this period.
    // VARCHAR BS dates compare lexicographically, correct for zero-padded YYYY-MM-DD.
    @Query("""
        SELECT r FROM MeterReading r
        WHERE r.meterId = :meterId
          AND r.status = com.renterp.domain.meterreading.entity.MeterReading$ReadingStatus.CONFIRMED
          AND r.consumption IS NOT NULL
          AND r.readingDateBs > :fromBs
          AND r.readingDateBs <= :toBs
        ORDER BY r.readingDateBs ASC
    """)
    List<MeterReading> findConsumptionInWindow(@Param("meterId") UUID meterId,
                                               @Param("fromBs") String fromBs,
                                               @Param("toBs") String toBs);

    // Latest CONFIRMED reading on/before a BS date — the coverage-as-of / opening anchor
    // lookup the billing engine needs (M9 coverage-as-of-billing-date, and the sub-meter
    // opening value). Ordered so the newest on-or-before date wins.
    @Query("""
        SELECT r FROM MeterReading r
        WHERE r.meterId = :meterId
          AND r.status = com.renterp.domain.meterreading.entity.MeterReading$ReadingStatus.CONFIRMED
          AND r.readingDateBs <= :asOfBs
        ORDER BY r.readingDateBs DESC, r.createdAt DESC
    """)
    List<MeterReading> findLatestConfirmedOnOrBefore(@Param("meterId") UUID meterId,
                                                     @Param("asOfBs") String asOfBs, Pageable pageable);
}
