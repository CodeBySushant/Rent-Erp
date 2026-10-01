package com.renterp.domain.tenancy.service;

import java.util.Optional;
import java.util.List;
import com.renterp.domain.auth.security.AuthUser;
import com.renterp.domain.auth.security.AccessGuard;
import com.renterp.common.exception.DuplicateResourceException;
import com.renterp.common.exception.InvalidOperationException;
import com.renterp.common.exception.ResourceNotFoundException;
import com.renterp.domain.auth.repository.UserRepository;
import com.renterp.domain.tenancy.dto.*;
import com.renterp.domain.tenancy.entity.TenantKyc;
import com.renterp.domain.tenancy.entity.TenantKyc.KycStatus;
import com.renterp.domain.tenancy.entity.TenantProfile;
import com.renterp.domain.tenancy.entity.TenantPropertyMembership.MembershipStatus;
import com.renterp.domain.tenancy.repository.TenantKycRepository;
import com.renterp.domain.tenancy.repository.TenantPropertyMembershipRepository;
import com.renterp.domain.tenancy.repository.TenantProfileRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class TenantProfileService {

    private static final Logger log = LogManager.getLogger(TenantProfileService.class);

    // T5 — spec §14.3 caps resubmit attempts; concrete cap = 5.
    private static final short MAX_KYC_RESUBMITS = 5;

    private final TenantProfileRepository profileRepository;
    private final TenantKycRepository kycRepository;
    private final TenantPropertyMembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final AccessGuard guard;

    public TenantProfileService(TenantProfileRepository profileRepository,
                                 TenantKycRepository kycRepository,
                                 TenantPropertyMembershipRepository membershipRepository,
                                 UserRepository userRepository,
                                 AccessGuard guard) {
        this.profileRepository = profileRepository;
        this.kycRepository = kycRepository;
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
        this.guard = guard;
    }

    // ── Profile ─────────────────────────────────────────────────────────────

    @Transactional
    public TenantProfileResponse createProfile(CreateTenantProfileRequest req) {
        log.debug("Creating tenant profile — userId: {}, fullName: {}", req.getUserId(), req.getFullName());

        // Linked path: user must exist and not already have a profile (one profile per user_id).
        if (req.getUserId() != null) {
            if (!userRepository.existsById(req.getUserId())) {
                throw new ResourceNotFoundException("User", "id", req.getUserId());
            }
            if (profileRepository.existsByUserId(req.getUserId())) {
                throw new DuplicateResourceException("TenantProfile", "userId", req.getUserId());
            }
        }
        // Unlinked path: no uniqueness on phone across landlords — spec §10.3 explicitly permits
        // the same person to appear as a separate tenant identity per landlord.

        TenantProfile p = TenantProfile.builder()
                .userId(req.getUserId())
                .createdBy(guard.current().map(AuthUser::userId).orElse(null))
                .fullName(req.getFullName())
                .phone(req.getPhone())
                .whatsappNumber(req.getWhatsappNumber())
                .preferredLanguage(req.getPreferredLanguage())
                .tenantCategory(req.getTenantCategory())
                .numOccupants(req.getNumOccupants())
                .emergencyContactName(req.getEmergencyContactName())
                .emergencyContactPhone(req.getEmergencyContactPhone())
                .build();
        TenantProfile saved = profileRepository.save(p);
        log.info("Tenant profile created — id: {}, linked: {}", saved.getId(), saved.getUserId() != null);
        return TenantProfileResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public TenantProfileResponse getProfileById(UUID id) {
        return TenantProfileResponse.from(requireProfile(id));
    }

    @Transactional(readOnly = true)
    public Page<TenantProfileResponse> listProfiles(Pageable pageable) {
        // Admins and unenforced developer requests see every profile; everyone
        // else only their own, the ones they created, and their properties'
        // tenants (members or join requests).
        Optional<List<UUID>> visible = guard.visiblePropertyIds();
        if (visible.isEmpty()) {
            return profileRepository.findAll(pageable).map(TenantProfileResponse::from);
        }
        List<UUID> propertyIds = visible.get().isEmpty()
                ? List.of(NO_PROPERTY) : visible.get();
        UUID callerId = guard.requireUser().userId();
        return profileRepository.findVisible(callerId, propertyIds, pageable)
                .map(TenantProfileResponse::from);
    }

    /** Placeholder for an empty IN list (no property has this id). */
    private static final UUID NO_PROPERTY = new UUID(0L, 0L);

    @Transactional
    public TenantProfileResponse updateProfile(UUID id, UpdateTenantProfileRequest req) {
        TenantProfile p = requireProfile(id);
        if (req.getFullName() != null) p.setFullName(req.getFullName());
        if (req.getWhatsappNumber() != null) p.setWhatsappNumber(req.getWhatsappNumber());
        if (req.getPreferredLanguage() != null) p.setPreferredLanguage(req.getPreferredLanguage());
        if (req.getTenantCategory() != null) p.setTenantCategory(req.getTenantCategory());
        if (req.getNumOccupants() != null) p.setNumOccupants(req.getNumOccupants());
        if (req.getEmergencyContactName() != null) p.setEmergencyContactName(req.getEmergencyContactName());
        if (req.getEmergencyContactPhone() != null) p.setEmergencyContactPhone(req.getEmergencyContactPhone());
        return TenantProfileResponse.from(profileRepository.saveAndFlush(p));
    }

    @Transactional
    public void deleteProfile(UUID id) {
        TenantProfile p = requireProfile(id);
        if (!p.isActive()) return;
        // A tenant with any ACTIVE membership cannot be soft-deleted — vacate them first,
        // otherwise the property loses its billing target mid-cycle.
        if (membershipRepository.existsByTenantProfileIdAndStatus(id, MembershipStatus.ACTIVE)) {
            throw new InvalidOperationException(
                    "Cannot delete tenant profile with an ACTIVE membership — terminate the membership first");
        }
        p.setActive(false);
        profileRepository.saveAndFlush(p);
    }

    // ── KYC ─────────────────────────────────────────────────────────────────

    @Transactional
    public TenantKycResponse submitKyc(UUID tenantProfileId, SubmitKycRequest req) {
        requireProfile(tenantProfileId);
        TenantKyc kyc = kycRepository.findByTenantProfileId(tenantProfileId).orElse(null);

        if (kyc == null) {
            kyc = TenantKyc.builder()
                    .tenantProfileId(tenantProfileId)
                    .idType(req.getIdType())
                    .idNumber(req.getIdNumber())
                    .photoFrontUrl(req.getPhotoFrontUrl())
                    .photoBackUrl(req.getPhotoBackUrl())
                    .photoSelfieUrl(req.getPhotoSelfieUrl())
                    .status(KycStatus.PENDING)
                    .submittedAt(Instant.now())
                    .build();
        } else {
            // Resubmission — only permitted from REJECTED or FLAGGED. APPROVED is terminal.
            if (kyc.getStatus() == KycStatus.APPROVED) {
                throw new InvalidOperationException(
                        "KYC already APPROVED — a new submission is not accepted (id number is permanent per spec §10.4)");
            }
            if (kyc.getStatus() == KycStatus.PENDING) {
                throw new InvalidOperationException(
                        "KYC submission is already PENDING review — wait for the current review before resubmitting");
            }
            if (kyc.getResubmitCount() >= MAX_KYC_RESUBMITS) {
                throw new InvalidOperationException(
                        "KYC resubmit limit reached (" + MAX_KYC_RESUBMITS + ") — contact support (§14.3 T5)");
            }
            kyc.setIdType(req.getIdType());
            kyc.setIdNumber(req.getIdNumber());
            kyc.setPhotoFrontUrl(req.getPhotoFrontUrl());
            kyc.setPhotoBackUrl(req.getPhotoBackUrl());
            kyc.setPhotoSelfieUrl(req.getPhotoSelfieUrl());
            kyc.setStatus(KycStatus.PENDING);
            kyc.setSubmittedAt(Instant.now());
            kyc.setResubmitCount((short) (kyc.getResubmitCount() + 1));
            // Wipe any prior verification decision.
            kyc.setVerifiedAt(null);
            kyc.setVerifiedBy(null);
            kyc.setRejectionReason(null);
            kyc.setFlagReason(null);
        }
        return TenantKycResponse.from(kycRepository.saveAndFlush(kyc));
    }

    @Transactional(readOnly = true)
    public TenantKycResponse getKyc(UUID tenantProfileId) {
        requireProfile(tenantProfileId);
        return kycRepository.findByTenantProfileId(tenantProfileId)
                .map(TenantKycResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("TenantKyc", "tenantProfileId", tenantProfileId));
    }

    @Transactional
    public TenantKycResponse approveKyc(UUID tenantProfileId) {
        TenantKyc kyc = requireKyc(tenantProfileId);
        if (kyc.getStatus() != KycStatus.PENDING) {
            throw new InvalidOperationException("Only PENDING KYC may be approved — current status: " + kyc.getStatus());
        }
        kyc.setStatus(KycStatus.APPROVED);
        kyc.setVerifiedAt(Instant.now());
        return TenantKycResponse.from(kycRepository.saveAndFlush(kyc));
    }

    @Transactional
    public TenantKycResponse rejectKyc(UUID tenantProfileId, KycDecisionRequest req) {
        TenantKyc kyc = requireKyc(tenantProfileId);
        if (kyc.getStatus() != KycStatus.PENDING) {
            throw new InvalidOperationException("Only PENDING KYC may be rejected — current status: " + kyc.getStatus());
        }
        kyc.setStatus(KycStatus.REJECTED);
        kyc.setVerifiedAt(Instant.now());
        kyc.setRejectionReason(req.getReason());
        return TenantKycResponse.from(kycRepository.saveAndFlush(kyc));
    }

    @Transactional
    public TenantKycResponse flagKyc(UUID tenantProfileId, KycDecisionRequest req) {
        // T6 — landlord flags physical mismatch. Routes back to admin (queue lands
        // when notifications ship — Phase 8). We record the reason and move status
        // to FLAGGED regardless of prior state, since a physical-doc mismatch is
        // discovered independently of the current KYC lifecycle position.
        TenantKyc kyc = requireKyc(tenantProfileId);
        if (kyc.getStatus() == KycStatus.APPROVED || kyc.getStatus() == KycStatus.PENDING || kyc.getStatus() == KycStatus.REJECTED) {
            kyc.setStatus(KycStatus.FLAGGED);
            kyc.setFlagReason(req.getReason());
            return TenantKycResponse.from(kycRepository.saveAndFlush(kyc));
        }
        // Already FLAGGED — update the reason but no status change.
        kyc.setFlagReason(req.getReason());
        return TenantKycResponse.from(kycRepository.saveAndFlush(kyc));
    }

    // ── Helpers exposed for other services ────────────────────────────────

    @Transactional(readOnly = true)
    public TenantProfile requireProfile(UUID id) {
        return profileRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("TenantProfile", "id", id));
    }

    private TenantKyc requireKyc(UUID tenantProfileId) {
        requireProfile(tenantProfileId);
        return kycRepository.findByTenantProfileId(tenantProfileId)
                .orElseThrow(() -> new ResourceNotFoundException("TenantKyc", "tenantProfileId", tenantProfileId));
    }
}
