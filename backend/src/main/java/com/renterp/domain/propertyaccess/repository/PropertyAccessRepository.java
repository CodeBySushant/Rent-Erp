package com.renterp.domain.propertyaccess.repository;

import com.renterp.domain.propertyaccess.entity.PropertyAccess;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PropertyAccessRepository extends JpaRepository<PropertyAccess, UUID> {

    Page<PropertyAccess> findByPropertyId(UUID propertyId, Pageable pageable);

    Page<PropertyAccess> findByUserId(UUID userId, Pageable pageable);

    boolean existsByPropertyIdAndUserId(UUID propertyId, UUID userId);

    // Authorization: the caller's active grant on one property, if any.
    Optional<PropertyAccess> findFirstByPropertyIdAndUserIdAndActiveTrue(UUID propertyId, UUID userId);

    // Authorization: every property the caller holds an active grant on.
    @Query("select a.propertyId from PropertyAccess a where a.userId = :userId and a.active = true")
    List<UUID> findActivePropertyIdsByUserId(@Param("userId") UUID userId);
}
