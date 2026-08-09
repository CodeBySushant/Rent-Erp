package com.renterp.domain.meter.repository;

import com.renterp.domain.meter.entity.MeterRoomCoverage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MeterRoomCoverageRepository extends JpaRepository<MeterRoomCoverage, UUID> {

    List<MeterRoomCoverage> findByMeterIdOrderByEffectiveFromBsAsc(UUID meterId);

    // Billing engine (pass 2, M9): the rooms a meter covered as of a given BS date —
    // a coverage row is in force when effectiveFromBs <= asOf AND (effectiveToBs IS NULL
    // OR effectiveToBs > asOf). Historical bills use coverage-as-of-billing-date, so a
    // meter whose coverage shrank later still bills its old rooms for the old period.
    @Query("""
        SELECT c FROM MeterRoomCoverage c
        WHERE c.meterId = :meterId
          AND c.effectiveFromBs <= :asOfBs
          AND (c.effectiveToBs IS NULL OR c.effectiveToBs > :asOfBs)
    """)
    List<MeterRoomCoverage> findActiveAsOf(@Param("meterId") UUID meterId, @Param("asOfBs") String asOfBs);

    // Used by MeterService to block a duplicate active coverage row and by the
    // Meter delete gate — if any row exists with effectiveToBs=NULL, the meter still
    // has live coverage and cannot be deactivated silently.
    Optional<MeterRoomCoverage> findFirstByMeterIdAndRoomIdAndEffectiveToBsIsNull(UUID meterId, UUID roomId);

    boolean existsByMeterIdAndEffectiveToBsIsNull(UUID meterId);
}
