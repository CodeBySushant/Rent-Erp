package com.renterp.domain.tenancy.repository;

import com.renterp.domain.tenancy.entity.TenantProfile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.Collection;
import java.util.UUID;

@Repository
public interface TenantProfileRepository extends JpaRepository<TenantProfile, UUID> {
    Optional<TenantProfile> findByUserId(UUID userId);
    boolean existsByUserId(UUID userId);
    Page<TenantProfile> findAll(Pageable pageable);

    /**
     * Profiles a non-admin caller may list: their own, the ones they created,
     * and anyone with a membership or join request in one of their properties.
     * {@code propertyIds} must not be empty (pass a placeholder id instead).
     */
    @Query("""
            select p from TenantProfile p
            where p.userId = :userId
               or p.createdBy = :userId
               or p.id in (select m.tenantProfileId from TenantPropertyMembership m
                           where m.propertyId in :propertyIds)
               or p.id in (select j.tenantProfileId from JoinRequest j
                           where j.propertyId in :propertyIds)
            """)
    Page<TenantProfile> findVisible(
            @Param("userId") UUID userId,
            @Param("propertyIds") Collection<UUID> propertyIds,
            Pageable pageable);
}
