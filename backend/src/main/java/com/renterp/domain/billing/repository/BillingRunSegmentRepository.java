package com.renterp.domain.billing.repository;

import com.renterp.domain.billing.entity.BillingRunSegment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BillingRunSegmentRepository extends JpaRepository<BillingRunSegment, UUID> {

    List<BillingRunSegment> findByBillingRunId(UUID billingRunId);
}
