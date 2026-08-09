package com.renterp.domain.tenancy.repository;

import com.renterp.domain.tenancy.entity.PropertyBlockedTenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PropertyBlockedTenantRepository extends JpaRepository<PropertyBlockedTenant, UUID> {

    // Used by join-request creation and by admin list endpoints.
    Optional<PropertyBlockedTenant> findByPropertyIdAndTenantProfileIdAndActive(
            UUID propertyId, UUID tenantProfileId, boolean active);

    boolean existsByPropertyIdAndTenantProfileIdAndActive(UUID propertyId, UUID tenantProfileId, boolean active);

    List<PropertyBlockedTenant> findByPropertyIdAndActiveOrderByBlockedAtDesc(UUID propertyId, boolean active);
}
