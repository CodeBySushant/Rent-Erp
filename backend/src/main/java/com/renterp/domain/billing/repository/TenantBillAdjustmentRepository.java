package com.renterp.domain.billing.repository;

import com.renterp.domain.billing.entity.TenantBillAdjustment;
import com.renterp.domain.billing.entity.TenantBillAdjustment.Status;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TenantBillAdjustmentRepository extends JpaRepository<TenantBillAdjustment, UUID> {

    List<TenantBillAdjustment> findByMembershipId(UUID membershipId);

    List<TenantBillAdjustment> findByMembershipIdAndStatus(UUID membershipId, Status status);

    // Cancelling a run releases the adjustments its bills consumed, back to PENDING.
    List<TenantBillAdjustment> findByAppliedBillId(UUID appliedBillId);
}
