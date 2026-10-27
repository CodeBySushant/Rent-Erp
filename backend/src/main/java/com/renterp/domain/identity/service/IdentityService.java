package com.renterp.domain.identity.service;

import com.renterp.common.exception.ApiException;
import com.renterp.domain.auth.entity.User;
import com.renterp.domain.auth.entity.User.UserRole;
import com.renterp.domain.auth.repository.UserRepository;
import com.renterp.domain.auth.security.AccessGuard;
import com.renterp.domain.auth.security.AuthUser;
import com.renterp.domain.file.entity.StoredFile;
import com.renterp.domain.file.entity.StoredFile.FilePurpose;
import com.renterp.domain.file.repository.StoredFileRepository;
import com.renterp.domain.identity.dto.AdminOwnerIdentityResponse;
import com.renterp.domain.identity.dto.AdminTenantKycResponse;
import com.renterp.domain.tenancy.entity.TenantKyc;
import com.renterp.domain.tenancy.service.TenantProfileLinker;
import com.renterp.domain.identity.dto.IdentityResponse;
import com.renterp.domain.identity.dto.SubmitIdentityRequest;
import com.renterp.domain.identity.entity.OwnerIdentity;
import com.renterp.domain.identity.repository.OwnerIdentityRepository;
import com.renterp.domain.tenancy.dto.CreateTenantProfileRequest;
import com.renterp.domain.tenancy.dto.SubmitKycRequest;
import com.renterp.domain.tenancy.entity.TenantProfile;
import com.renterp.domain.tenancy.repository.TenantKycRepository;
import com.renterp.domain.tenancy.repository.TenantProfileRepository;
import com.renterp.domain.tenancy.service.TenantProfileService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The signed-in user's ID document + selfie ("Verify identity").
 *
 * <ul>
 *   <li><b>Tenants</b> go through the existing KYC record (tenant_kyc), which has
 *       a review flow. A tenant has no profile until they join a property, so the
 *       profile is created here on first submission (same as joining does).</li>
 *   <li><b>Owners</b> are stored in owner_identity. Nothing reviews it; it is kept so
 *       a developer / admin can read it later.</li>
 * </ul>
 * The photos must be the caller's own uploads with the matching purpose, so one user
 * cannot attach another user's files.
 */
@Service
public class IdentityService {

    private static final Logger log = LogManager.getLogger(IdentityService.class);

    private final AccessGuard guard;
    private final OwnerIdentityRepository owners;
    private final StoredFileRepository files;
    private final UserRepository users;
    private final TenantProfileRepository profiles;
    private final TenantProfileService profileService;
    private final TenantKycRepository kycRepository;
    private final TenantProfileLinker linker;

    public IdentityService(AccessGuard guard, OwnerIdentityRepository owners, StoredFileRepository files,
                           UserRepository users, TenantProfileRepository profiles,
                           TenantProfileService profileService, TenantKycRepository kycRepository,
                           TenantProfileLinker linker) {
        this.linker = linker;
        this.guard = guard;
        this.owners = owners;
        this.files = files;
        this.users = users;
        this.profiles = profiles;
        this.profileService = profileService;
        this.kycRepository = kycRepository;
    }

    @Transactional(readOnly = true)
    public IdentityResponse mine() {
        AuthUser user = guard.requireUser();
        if (user.role() == UserRole.TENANT) {
            return profiles.findByUserId(user.userId())
                    .flatMap(p -> kycRepository.findByTenantProfileId(p.getId()))
                    .map(k -> new IdentityResponse(true, "TENANT", k.getStatus().name(), k.getIdType().name(),
                            IdentityResponse.mask(k.getIdNumber()), null, null, null,
                            k.getSubmittedAt(), k.getRejectionReason()))
                    .orElse(IdentityResponse.none("TENANT"));
        }
        return owners.findById(user.userId())
                .map(IdentityService::toOwnerResponse)
                .orElse(IdentityResponse.none("OWNER"));
    }

    @Transactional
    public IdentityResponse submit(SubmitIdentityRequest req) {
        AuthUser user = guard.requireUser();
        requireOwnFile(user, req.idFrontFileId(), FilePurpose.KYC_ID_FRONT);
        requireOwnFile(user, req.selfieFileId(), FilePurpose.KYC_SELFIE);
        if (req.idBackFileId() != null) {
            requireOwnFile(user, req.idBackFileId(), FilePurpose.KYC_ID_BACK);
        }
        String idNumber = req.idNumber().trim();

        if (user.role() == UserRole.TENANT) {
            submitTenant(user, req, idNumber);
            log.info("Tenant identity submitted — user: {}", user.userId());
            return mine();
        }

        OwnerIdentity row = owners.findById(user.userId()).orElseGet(
                () -> OwnerIdentity.builder().userId(user.userId()).build());
        row.setIdType(req.idType());
        row.setIdNumber(idNumber);
        row.setIdFrontFileId(req.idFrontFileId());
        row.setIdBackFileId(req.idBackFileId());
        row.setSelfieFileId(req.selfieFileId());
        row.setSubmittedAt(Instant.now());
        owners.saveAndFlush(row);
        log.info("Owner identity stored — user: {}", user.userId());
        return toOwnerResponse(row);
    }

    /** Developer / admin view: stored owner identities, newest first, with full ID numbers. */
    @Transactional(readOnly = true)
    public List<AdminOwnerIdentityResponse> adminList(int page, int size) {
        guard.requireAdmin();
        List<OwnerIdentity> rows = owners.findAll(
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 200),
                        Sort.by(Sort.Direction.DESC, "submittedAt"))).getContent();
        Map<UUID, User> byId = users.findAllById(rows.stream().map(OwnerIdentity::getUserId).toList())
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        return rows.stream().map(r -> {
            User u = byId.get(r.getUserId());
            return new AdminOwnerIdentityResponse(r.getUserId(), u == null ? null : u.getName(),
                    u == null ? null : u.getPhone(), r.getIdType().name(), r.getIdNumber(),
                    r.getIdFrontFileId(), r.getIdBackFileId(), r.getSelfieFileId(), r.getSubmittedAt());
        }).toList();
    }

    /**
     * Developer / admin view of tenant identity submissions (H6), newest first,
     * optionally only one status (PENDING = waiting for review). Decide with
     * POST /api/v1/tenant-profiles/{id}/kyc/approve | reject | flag.
     */
    @Transactional(readOnly = true)
    public List<AdminTenantKycResponse> adminTenantKyc(String status, int page, int size) {
        guard.requireAdmin();
        PageRequest pr = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 200),
                Sort.by(Sort.Direction.DESC, "submittedAt"));
        List<TenantKyc> rows;
        if (status == null || status.isBlank()) {
            rows = kycRepository.findAll(pr).getContent();
        } else {
            TenantKyc.KycStatus wanted;
            try {
                wanted = TenantKyc.KycStatus.valueOf(status.trim().toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw ApiException.badRequest("INVALID_STATUS", "status must be PENDING, APPROVED, REJECTED or FLAGGED.");
            }
            rows = kycRepository.findByStatus(wanted, pr).getContent();
        }
        Map<UUID, TenantProfile> byProfile = profiles.findAllById(rows.stream().map(TenantKyc::getTenantProfileId).toList())
                .stream().collect(Collectors.toMap(TenantProfile::getId, Function.identity()));
        return rows.stream().map(k -> {
            TenantProfile p = byProfile.get(k.getTenantProfileId());
            return new AdminTenantKycResponse(k.getId(), k.getTenantProfileId(), p == null ? null : p.getUserId(),
                    p == null ? null : p.getFullName(), p == null ? null : p.getPhone(),
                    k.getIdType().name(), k.getIdNumber(), k.getIdFrontFileId(), k.getIdBackFileId(),
                    k.getSelfieFileId(), k.getStatus().name(), k.getSubmittedAt(), k.getVerifiedAt(),
                    k.getVerifiedBy(), k.getRejectionReason(), k.getFlagReason());
        }).toList();
    }

    private void submitTenant(AuthUser user, SubmitIdentityRequest req, String idNumber) {
        // C8: a profile the owner already created for this phone is used first.
        String phone = users.findById(user.userId()).map(User::getPhone).orElse(null);
        TenantProfile profile = linker.linkForUser(user.userId(), phone).orElse(null);
        UUID profileId;
        if (profile == null) {
            User u = users.findById(user.userId())
                    .orElseThrow(() -> ApiException.unauthorized("UNAUTHENTICATED", "Please log in to continue."));
            CreateTenantProfileRequest create = new CreateTenantProfileRequest();
            create.setUserId(u.getId());
            create.setFullName(u.getName() == null || u.getName().isBlank() ? u.getPhone() : u.getName());
            create.setPhone(u.getPhone());
            create.setPreferredLanguage(u.getPreferredLanguage());
            profileId = profileService.createProfile(create).getId();
        } else {
            profileId = profile.getId();
        }
        SubmitKycRequest kyc = new SubmitKycRequest();
        kyc.setIdType(req.idType());
        kyc.setIdNumber(idNumber);
        kyc.setIdFrontFileId(req.idFrontFileId());
        kyc.setIdBackFileId(req.idBackFileId());
        kyc.setSelfieFileId(req.selfieFileId());
        profileService.submitKyc(profileId, kyc);
    }

    /** A file the caller uploaded themselves, with the purpose this field needs. */
    private void requireOwnFile(AuthUser user, UUID fileId, FilePurpose expected) {
        StoredFile f = files.findByIdAndDeletedAtIsNull(fileId).orElseThrow(() -> ApiException.badRequest(
                "FILE_NOT_FOUND", "One of the photos could not be found. Upload it again."));
        if (!user.userId().equals(f.getOwnerUserId()) || f.getPurpose() != expected) {
            throw ApiException.badRequest("FILE_INVALID",
                    "One of the photos is not valid for this step. Upload it again.");
        }
    }

    private static IdentityResponse toOwnerResponse(OwnerIdentity o) {
        return new IdentityResponse(true, "OWNER", "SUBMITTED", o.getIdType().name(),
                IdentityResponse.mask(o.getIdNumber()), o.getIdFrontFileId(), o.getIdBackFileId(),
                o.getSelfieFileId(), o.getSubmittedAt(), null);
    }
}
