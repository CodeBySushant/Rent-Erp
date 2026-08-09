package com.renterp.domain.tenantfinance.repository;

import com.renterp.domain.tenantfinance.entity.TenantDeposit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantDepositRepository extends JpaRepository<TenantDeposit, UUID> {
    Optional<TenantDeposit> findByMembershipId(UUID membershipId);
    boolean existsByMembershipId(UUID membershipId);
}
