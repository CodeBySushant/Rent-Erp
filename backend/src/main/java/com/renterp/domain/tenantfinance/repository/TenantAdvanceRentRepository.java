package com.renterp.domain.tenantfinance.repository;

import com.renterp.domain.tenantfinance.entity.TenantAdvanceRent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TenantAdvanceRentRepository extends JpaRepository<TenantAdvanceRent, UUID> {
    List<TenantAdvanceRent> findByMembershipIdOrderByCoveredFromBsAsc(UUID membershipId);
}
