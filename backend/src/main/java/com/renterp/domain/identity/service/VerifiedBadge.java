package com.renterp.domain.identity.service;

import com.renterp.domain.identity.repository.OwnerIdentityRepository;
import com.renterp.domain.tenancy.entity.TenantKyc;
import com.renterp.domain.tenancy.repository.TenantKycRepository;
import com.renterp.domain.tenancy.repository.TenantProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Whether a user gets the blue "verified" tick (13 Nov 2026). The one place
 * that decides it, so every screen agrees:
 * <ul>
 *   <li>a tenant whose ID was reviewed and APPROVED (tenant_kyc);</li>
 *   <li>an owner who has submitted their ID (owner_identity). Owner IDs have
 *       no review step yet; when one is added, require its approval here.</li>
 * </ul>
 */
@Service
public class VerifiedBadge {

    private final OwnerIdentityRepository owners;
    private final TenantProfileRepository profiles;
    private final TenantKycRepository kyc;

    public VerifiedBadge(OwnerIdentityRepository owners, TenantProfileRepository profiles,
                         TenantKycRepository kyc) {
        this.owners = owners;
        this.profiles = profiles;
        this.kyc = kyc;
    }

    @Transactional(readOnly = true)
    public boolean isVerified(UUID userId) {
        if (userId == null) {
            return false;
        }
        if (owners.existsById(userId)) {
            return true;
        }
        return profiles.findByUserId(userId)
                .flatMap(p -> kyc.findByTenantProfileId(p.getId()))
                .map(k -> k.getStatus() == TenantKyc.KycStatus.APPROVED)
                .orElse(false);
    }
}
