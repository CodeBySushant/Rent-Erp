package com.renterp.domain.billing.repository;

import com.renterp.domain.billing.entity.BillingRunProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface BillingRunProgressRepository extends JpaRepository<BillingRunProgress, UUID> {

    Optional<BillingRunProgress> findByBillingRunId(UUID billingRunId);
}
