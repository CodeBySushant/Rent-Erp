package com.renterp.domain.tenancy.service;

import com.renterp.common.exception.DuplicateResourceException;
import com.renterp.common.exception.InvalidOperationException;
import com.renterp.common.exception.ResourceNotFoundException;
import com.renterp.domain.property.repository.PropertyRepository;
import com.renterp.domain.tenancy.dto.CreateJoinRequestRequest;
import com.renterp.domain.tenancy.dto.JoinRequestDecisionRequest;
import com.renterp.domain.tenancy.dto.JoinRequestResponse;
import com.renterp.domain.tenancy.dto.MembershipResponse;
import com.renterp.domain.tenancy.entity.JoinRequest;
import com.renterp.domain.tenancy.entity.JoinRequest.JoinRequestStatus;
import com.renterp.domain.tenancy.entity.TenantProfile;
import com.renterp.domain.tenancy.entity.TenantPropertyMembership;
import com.renterp.domain.tenancy.repository.JoinRequestRepository;
import com.renterp.domain.tenancy.repository.TenantProfileRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class JoinRequestService {

    private static final Logger log = LogManager.getLogger(JoinRequestService.class);

    // Spec §14.3 T1 — 5-minute expiry from request time.
    private static final Duration REQUEST_TTL = Duration.ofMinutes(5);

    private final JoinRequestRepository joinRepository;
    private final TenantProfileRepository profileRepository;
    private final PropertyRepository propertyRepository;
    private final BlockedTenantService blockedService;
    private final MembershipService membershipService;

    public JoinRequestService(JoinRequestRepository joinRepository,
                               TenantProfileRepository profileRepository,
                               PropertyRepository propertyRepository,
                               BlockedTenantService blockedService,
                               MembershipService membershipService) {
        this.joinRepository = joinRepository;
        this.profileRepository = profileRepository;
        this.propertyRepository = propertyRepository;
        this.blockedService = blockedService;
        this.membershipService = membershipService;
    }

    @Transactional
    public JoinRequestResponse create(CreateJoinRequestRequest req) {
        TenantProfile tenant = profileRepository.findById(req.getTenantProfileId())
                .orElseThrow(() -> new ResourceNotFoundException("TenantProfile", "id", req.getTenantProfileId()));
        // Only linked tenants (with a users row) may create join requests — unlinked
        // tenants are onboarded directly by the landlord via POST /memberships (T7).
        if (tenant.getUserId() == null) {
            throw new InvalidOperationException(
                    "Only linked tenants may create join requests — unlinked tenants are onboarded by the landlord directly");
        }
        if (!propertyRepository.existsById(req.getPropertyId())) {
            throw new ResourceNotFoundException("Property", "id", req.getPropertyId());
        }

        // T3 — property has blocked this tenant. Neutral message per spec, but still 409.
        if (blockedService.isBlocked(req.getPropertyId(), req.getTenantProfileId())) {
            throw new InvalidOperationException(
                    "This landlord is not accepting new join requests from you at this time");
        }

        // T4 — one PENDING per (tenant, property). Expire any stale PENDING first.
        joinRepository.findByTenantProfileIdAndPropertyIdAndStatus(
                req.getTenantProfileId(), req.getPropertyId(), JoinRequestStatus.PENDING)
                .ifPresent(existing -> {
                    if (expireIfStale(existing) == JoinRequestStatus.PENDING) {
                        throw new DuplicateResourceException("JoinRequest",
                                "tenantProfileId+propertyId (pending)", req.getTenantProfileId() + "+" + req.getPropertyId());
                    }
                });

        Instant now = Instant.now();
        JoinRequest jr = JoinRequest.builder()
                .tenantProfileId(req.getTenantProfileId())
                .propertyId(req.getPropertyId())
                .status(JoinRequestStatus.PENDING)
                .message(req.getMessage())
                .requestedAt(now)
                .expiresAt(now.plus(REQUEST_TTL))
                .build();
        log.info("Join request created — tenant: {}, property: {}", req.getTenantProfileId(), req.getPropertyId());
        return JoinRequestResponse.from(joinRepository.saveAndFlush(jr));
    }

    @Transactional
    public JoinRequestResponse getById(UUID id) {
        JoinRequest jr = require(id);
        expireIfStale(jr);
        return JoinRequestResponse.from(jr);
    }

    @Transactional
    public Page<JoinRequestResponse> list(UUID propertyId, UUID tenantProfileId, JoinRequestStatus status, Pageable pageable) {
        Page<JoinRequest> page;
        if (propertyId != null && status != null) {
            page = joinRepository.findByPropertyIdAndStatus(propertyId, status, pageable);
        } else if (propertyId != null) {
            page = joinRepository.findByPropertyId(propertyId, pageable);
        } else if (tenantProfileId != null) {
            page = joinRepository.findByTenantProfileId(tenantProfileId, pageable);
        } else {
            page = joinRepository.findAll(pageable);
        }
        // Lazy-expire — a batch sweep via db-scheduler is noted as a small follow-up.
        page.getContent().forEach(this::expireIfStale);
        return page.map(JoinRequestResponse::from);
    }

    // Landlord accept — creates a membership atomically. startedAtBs required.
    @Transactional
    public MembershipResponse accept(UUID id, JoinRequestDecisionRequest req) {
        JoinRequest jr = require(id);
        if (expireIfStale(jr) != JoinRequestStatus.PENDING) {
            throw new InvalidOperationException(
                    "Join request is " + jr.getStatus() + " and cannot be accepted");
        }
        if (req.getStartedAtBs() == null || req.getStartedAtBs().isBlank()) {
            throw new InvalidOperationException("startedAtBs is required to accept a join request");
        }
        TenantPropertyMembership m = membershipService.createInternal(
                jr.getTenantProfileId(), jr.getPropertyId(), req.getStartedAtBs(), null, jr.getId());

        jr.setStatus(JoinRequestStatus.ACCEPTED);
        jr.setRespondedAt(Instant.now());
        jr.setResponseMessage(req.getResponseMessage());
        joinRepository.saveAndFlush(jr);
        log.info("Join request accepted — id: {}, membership: {}", id, m.getId());
        return MembershipResponse.from(m);
    }

    @Transactional
    public JoinRequestResponse reject(UUID id, JoinRequestDecisionRequest req) {
        JoinRequest jr = require(id);
        if (expireIfStale(jr) != JoinRequestStatus.PENDING) {
            throw new InvalidOperationException("Join request is " + jr.getStatus() + " and cannot be rejected");
        }
        jr.setStatus(JoinRequestStatus.REJECTED);
        jr.setRespondedAt(Instant.now());
        jr.setResponseMessage(req.getResponseMessage());
        return JoinRequestResponse.from(joinRepository.saveAndFlush(jr));
    }

    @Transactional
    public JoinRequestResponse cancel(UUID id) {
        JoinRequest jr = require(id);
        if (expireIfStale(jr) != JoinRequestStatus.PENDING) {
            throw new InvalidOperationException("Join request is " + jr.getStatus() + " and cannot be cancelled");
        }
        jr.setStatus(JoinRequestStatus.CANCELLED);
        jr.setRespondedAt(Instant.now());
        return JoinRequestResponse.from(joinRepository.saveAndFlush(jr));
    }

    // Returns the request's effective status after applying lazy expiry.
    private JoinRequestStatus expireIfStale(JoinRequest jr) {
        if (jr.getStatus() == JoinRequestStatus.PENDING && Instant.now().isAfter(jr.getExpiresAt())) {
            jr.setStatus(JoinRequestStatus.EXPIRED);
            joinRepository.saveAndFlush(jr);
        }
        return jr.getStatus();
    }

    private JoinRequest require(UUID id) {
        return joinRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("JoinRequest", "id", id));
    }
}
