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

    // Guards tariff edit/delete: a schedule referenced by a CONFIRMED run is frozen (B5).
    boolean existsByTariffVersionIdAndStatus(UUID tariffVersionId, Status status);
}
