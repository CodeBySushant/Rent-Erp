package com.renterp.domain.tenancy.repository;

import com.renterp.domain.tenancy.entity.TenantKyc;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantKycRepository extends JpaRepository<TenantKyc, UUID> {
    Optional<TenantKyc> findByTenantProfileId(UUID tenantProfileId);
}
