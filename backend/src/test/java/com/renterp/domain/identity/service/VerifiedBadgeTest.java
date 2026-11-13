package com.renterp.domain.identity.service;

import com.renterp.domain.identity.repository.OwnerIdentityRepository;
import com.renterp.domain.tenancy.entity.TenantKyc;
import com.renterp.domain.tenancy.entity.TenantProfile;
import com.renterp.domain.tenancy.repository.TenantKycRepository;
import com.renterp.domain.tenancy.repository.TenantProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Who gets the blue tick (13 Nov 2026). */
class VerifiedBadgeTest {

    private OwnerIdentityRepository owners;
    private TenantProfileRepository profiles;
    private TenantKycRepository kyc;
    private VerifiedBadge badge;
    private final UUID user = UUID.randomUUID();
    private final UUID profileId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        owners = mock(OwnerIdentityRepository.class);
        profiles = mock(TenantProfileRepository.class);
        kyc = mock(TenantKycRepository.class);
        badge = new VerifiedBadge(owners, profiles, kyc);
    }

    private void tenantKyc(TenantKyc.KycStatus status) {
        TenantProfile p = new TenantProfile();
        p.setId(profileId);
        when(profiles.findByUserId(user)).thenReturn(Optional.of(p));
        TenantKyc k = new TenantKyc();
        k.setStatus(status);
        when(kyc.findByTenantProfileId(profileId)).thenReturn(Optional.of(k));
    }

    @Test
    void anOwnerWhoSubmittedTheirIdIsVerified() {
        when(owners.existsById(user)).thenReturn(true);
        assertTrue(badge.isVerified(user));
    }

    @Test
    void aTenantOnlyOnceTheirIdIsApproved() {
        tenantKyc(TenantKyc.KycStatus.PENDING);
        assertFalse(badge.isVerified(user));
        tenantKyc(TenantKyc.KycStatus.APPROVED);
        assertTrue(badge.isVerified(user));
    }

    @Test
    void nobodyWithoutAnId() {
        when(profiles.findByUserId(user)).thenReturn(Optional.empty());
        assertFalse(badge.isVerified(user));
        assertFalse(badge.isVerified(null));
    }
}
