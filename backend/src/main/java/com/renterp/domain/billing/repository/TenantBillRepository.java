package com.renterp.domain.billing.repository;

import com.renterp.domain.billing.entity.TenantBill;
import com.renterp.domain.billing.entity.TenantBill.Status;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TenantBillRepository extends JpaRepository<TenantBill, UUID> {

    List<TenantBill> findByBillingRunId(UUID billingRunId);

    Page<TenantBill> findByMembershipId(UUID membershipId, Pageable pageable);

    // T7 first-bill detection + T8 advance ledger both need the membership's non-cancelled
    // bill history.
    List<TenantBill> findByMembershipIdAndStatusNot(UUID membershipId, Status status);

    long countByMembershipIdAndStatusNot(UUID membershipId, Status status);
}
