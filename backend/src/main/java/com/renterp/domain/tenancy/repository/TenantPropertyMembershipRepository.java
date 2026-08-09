package com.renterp.domain.tenancy.repository;

import com.renterp.domain.tenancy.entity.TenantPropertyMembership;
import com.renterp.domain.tenancy.entity.TenantPropertyMembership.MembershipStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantPropertyMembershipRepository extends JpaRepository<TenantPropertyMembership, UUID> {

    Optional<TenantPropertyMembership> findByTenantProfileIdAndPropertyIdAndStatus(
            UUID tenantProfileId, UUID propertyId, MembershipStatus status);

    boolean existsByTenantProfileIdAndStatus(UUID tenantProfileId, MembershipStatus status);

    Page<TenantPropertyMembership> findByPropertyId(UUID propertyId, Pageable pageable);
    Page<TenantPropertyMembership> findByTenantProfileId(UUID tenantProfileId, Pageable pageable);
    Page<TenantPropertyMembership> findByPropertyIdAndStatus(UUID propertyId, MembershipStatus status, Pageable pageable);

    // Billing engine iterates every ACTIVE membership in a property (non-paged).
    List<TenantPropertyMembership> findByPropertyIdAndStatus(UUID propertyId, MembershipStatus status);
}
