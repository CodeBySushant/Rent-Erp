package com.renterp.domain.tenantfinance.repository;

import com.renterp.domain.tenantfinance.entity.TenantOpeningBalance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantOpeningBalanceRepository extends JpaRepository<TenantOpeningBalance, UUID> {
    Optional<TenantOpeningBalance> findByMembershipId(UUID membershipId);
    boolean existsByMembershipId(UUID membershipId);
}
