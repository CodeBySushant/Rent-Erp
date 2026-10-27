package com.renterp.domain.identity.repository;

import com.renterp.domain.identity.entity.OwnerIdentity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OwnerIdentityRepository extends JpaRepository<OwnerIdentity, UUID> {
}
