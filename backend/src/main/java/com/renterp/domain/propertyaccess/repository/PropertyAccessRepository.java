package com.renterp.domain.propertyaccess.repository;

import com.renterp.domain.propertyaccess.entity.PropertyAccess;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PropertyAccessRepository extends JpaRepository<PropertyAccess, UUID> {

    Page<PropertyAccess> findByPropertyId(UUID propertyId, Pageable pageable);

    Page<PropertyAccess> findByUserId(UUID userId, Pageable pageable);

    boolean existsByPropertyIdAndUserId(UUID propertyId, UUID userId);
}
