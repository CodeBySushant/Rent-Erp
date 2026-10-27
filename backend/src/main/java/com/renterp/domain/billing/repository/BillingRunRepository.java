package com.renterp.domain.billing.repository;

import com.renterp.domain.billing.entity.BillingRun;
import com.renterp.domain.billing.entity.BillingRun.Status;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BillingRunRepository extends JpaRepository<BillingRun, UUID> {

    Page<BillingRun> findByPropertyId(UUID propertyId, Pageable pageable);

    Optional<BillingRun> findByPropertyIdAndIdempotencyKey(UUID propertyId, String idempotencyKey);

    // A live (non-cancelled) run for this exact period, if any (B10 duplicate guard).
    Optional<BillingRun> findByPropertyIdAndBillingMonthBsAndStatusNot(
            UUID propertyId, String billingMonthBs, Status status);

    // B13 — any un-confirmed run for an EARLIER period must be confirmed first.
    List<BillingRun> findByPropertyIdAndStatus(UUID propertyId, Status status);

    // C6: live runs of a property whose period shares at least one day with [start, end].
    // BS dates are zero-padded "YYYY-MM-DD", so string comparison orders them.
    @org.springframework.data.jpa.repository.Query("""
            select r from BillingRun r
            where r.propertyId = :propertyId and r.status <> :cancelled
              and r.periodStartBs <= :endBs and r.periodEndBs >= :startBs
            order by r.periodStartBs
            """)
    List<BillingRun> findOverlapping(@org.springframework.data.repository.query.Param("propertyId") UUID propertyId,
                                     @org.springframework.data.repository.query.Param("startBs") String startBs,
                                     @org.springframework.data.repository.query.Param("endBs") String endBs,
                                     @org.springframework.data.repository.query.Param("cancelled") Status cancelled);

    // M8 / M10: is a draft run open for this property?
    boolean existsByPropertyIdAndStatus(UUID propertyId, Status status);

    // Guards tariff edit/delete: a schedule referenced by a CONFIRMED run is frozen (B5).
    boolean existsByTariffVersionIdAndStatus(UUID tariffVersionId, Status status);

    // M17: is the tariff used by any live run?
    boolean existsByTariffVersionIdAndStatusIn(UUID tariffVersionId, java.util.Collection<Status> statuses);
}
