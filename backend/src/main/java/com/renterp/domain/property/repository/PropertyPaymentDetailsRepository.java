package com.renterp.domain.property.repository;

import com.renterp.domain.property.entity.PropertyPaymentDetails;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PropertyPaymentDetailsRepository extends JpaRepository<PropertyPaymentDetails, UUID> {
}
