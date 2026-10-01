package com.renterp.domain.propertyaccess.service;

import com.renterp.common.exception.DuplicateResourceException;
import com.renterp.common.exception.InvalidOperationException;
import com.renterp.common.exception.ResourceNotFoundException;
import com.renterp.domain.auth.repository.UserRepository;
import com.renterp.domain.auth.security.AccessGuard;
import com.renterp.domain.property.repository.PropertyRepository;
import com.renterp.domain.propertyaccess.dto.CreatePropertyAccessRequest;
import com.renterp.domain.propertyaccess.dto.PropertyAccessResponse;
import com.renterp.domain.propertyaccess.dto.UpdatePropertyAccessRequest;
import com.renterp.domain.propertyaccess.entity.PropertyAccess;
import com.renterp.domain.propertyaccess.entity.PropertyAccess.AccessRole;
import com.renterp.domain.propertyaccess.repository.PropertyAccessRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class PropertyAccessService {

    private static final Logger log = LogManager.getLogger(PropertyAccessService.class);

    private final PropertyAccessRepository propertyAccessRepository;
    private final PropertyRepository propertyRepository;
    private final UserRepository userRepository;
    private final AccessGuard accessGuard;

    public PropertyAccessService(PropertyAccessRepository propertyAccessRepository,
                                  PropertyRepository propertyRepository,
                                  UserRepository userRepository,
                                  AccessGuard accessGuard) {
        this.propertyAccessRepository = propertyAccessRepository;
        this.propertyRepository = propertyRepository;
        this.userRepository = userRepository;
        this.accessGuard = accessGuard;
    }

    // ── Create ─────────────────────────────────────────────────────────────────

    @Transactional
    public PropertyAccessResponse createAccess(CreatePropertyAccessRequest request) {
        // Only the property's owner shares it.
        accessGuard.requirePropertyAccess(request.getPropertyId(), AccessRole.OWNER);
        log.debug("Granting property access — property: {}, user: {}, role: {}",
                request.getPropertyId(), request.getUserId(), request.getRole());

        if (request.getRole() == AccessRole.OWNER) {
            throw new InvalidOperationException(
                    "OWNER access is created automatically when a property is created and cannot be granted directly");
        }

        if (!propertyRepository.existsById(request.getPropertyId())) {
            log.warn("Access grant failed — property not found: {}", request.getPropertyId());
            throw new ResourceNotFoundException("Property", "id", request.getPropertyId());
        }
        if (!userRepository.existsById(request.getUserId())) {
            log.warn("Access grant failed — user not found: {}", request.getUserId());
            throw new ResourceNotFoundException("User", "id", request.getUserId());
        }
        if (request.getGrantedBy() != null && !userRepository.existsById(request.getGrantedBy())) {
            log.warn("Access grant failed — granting user not found: {}", request.getGrantedBy());
            throw new ResourceNotFoundException("User", "id", request.getGrantedBy());
        }
        if (propertyAccessRepository.existsByPropertyIdAndUserId(request.getPropertyId(), request.getUserId())) {
            log.warn("Access grant failed — grant already exists for property: {}, user: {}",
                    request.getPropertyId(), request.getUserId());
            throw new DuplicateResourceException("PropertyAccess", "propertyId+userId",
                    request.getPropertyId() + "+" + request.getUserId());
        }

        PropertyAccess access = PropertyAccess.builder()
                .propertyId(request.getPropertyId())
                .userId(request.getUserId())
                .role(request.getRole())
                .grantedBy(request.getGrantedBy())
                .build();

        PropertyAccess saved = propertyAccessRepository.save(access);
        log.info("Property access granted — id: {}, property: {}, user: {}, role: {}",
                saved.getId(), saved.getPropertyId(), saved.getUserId(), saved.getRole());

        return PropertyAccessResponse.from(saved);
    }

    // Used only by PropertyService.createProperty() to auto-create the OWNER row in the
    // same transaction as the property. Bypasses the OWNER-rejection and duplicate checks
    // above since the property (and therefore this grant) is guaranteed brand new.
    @Transactional
    public void createOwnerGrant(UUID propertyId, UUID ownerUserId) {
        PropertyAccess ownerGrant = PropertyAccess.builder()
                .propertyId(propertyId)
                .userId(ownerUserId)
                .role(AccessRole.OWNER)
                .grantedBy(null)
                .build();

        propertyAccessRepository.save(ownerGrant);
        log.info("Owner access auto-granted — property: {}, user: {}", propertyId, ownerUserId);
    }

    // ── Read single ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PropertyAccessResponse getAccessById(UUID id) {
        log.debug("Fetching property access by id: {}", id);

        PropertyAccess access = propertyAccessRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Property access not found — id: {}", id);
                    return new ResourceNotFoundException("PropertyAccess", "id", id);
                });
        accessGuard.requirePropertyAccess(access.getPropertyId(), AccessRole.VIEW_ONLY);

        return PropertyAccessResponse.from(access);
    }

    // ── Read all (paginated, optionally filtered by property or user) ──────────

    @Transactional(readOnly = true)
    public Page<PropertyAccessResponse> getAllAccess(UUID propertyId, UUID userId, Pageable pageable) {
        log.debug("Fetching property access — property: {}, user: {}, page: {}, size: {}",
                propertyId, userId, pageable.getPageNumber(), pageable.getPageSize());

        if (propertyId != null) {
            accessGuard.requirePropertyAccess(propertyId, AccessRole.VIEW_ONLY);
        } else if (userId != null) {
            accessGuard.requireSelfOrAdmin(userId);
        } else {
            accessGuard.requireAdmin();
        }

        Page<PropertyAccess> page;
        if (propertyId != null) {
            page = propertyAccessRepository.findByPropertyId(propertyId, pageable);
        } else if (userId != null) {
            page = propertyAccessRepository.findByUserId(userId, pageable);
        } else {
            page = propertyAccessRepository.findAll(pageable);
        }

        return page.map(PropertyAccessResponse::from);
    }

    // ── Update ─────────────────────────────────────────────────────────────────

    @Transactional
    public PropertyAccessResponse updateAccess(UUID id, UpdatePropertyAccessRequest request) {
        log.debug("Updating property access — id: {}, new role: {}", id, request.getRole());

        PropertyAccess access = propertyAccessRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Update failed — property access not found — id: {}", id);
                    return new ResourceNotFoundException("PropertyAccess", "id", id);
                });
        accessGuard.requirePropertyAccess(access.getPropertyId(), AccessRole.OWNER);

        if (access.getRole() == AccessRole.OWNER) {
            throw new InvalidOperationException(
                    "OWNER access cannot be modified through this endpoint — use ownership transfer instead");
        }
        if (request.getRole() == AccessRole.OWNER) {
            throw new InvalidOperationException(
                    "A grant cannot be changed to OWNER — use ownership transfer instead");
        }

        access.setRole(request.getRole());

        // saveAndFlush so JPA auditing's @LastModifiedDate lands on the managed entity
        // before the response DTO is built — see PropertyService.updateProperty for why.
        PropertyAccess updated = propertyAccessRepository.saveAndFlush(access);
        log.info("Property access updated — id: {}, new role: {}", updated.getId(), updated.getRole());

        return PropertyAccessResponse.from(updated);
    }

    // ── Soft Delete (revoke) ─────────────────────────────────────────────────────

    @Transactional
    public void revokeAccess(UUID id) {
        log.debug("Revoking property access — id: {}", id);

        PropertyAccess access = propertyAccessRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Revoke failed — property access not found — id: {}", id);
                    return new ResourceNotFoundException("PropertyAccess", "id", id);
                });
        accessGuard.requirePropertyAccess(access.getPropertyId(), AccessRole.OWNER);

        if (access.getRole() == AccessRole.OWNER) {
            throw new InvalidOperationException(
                    "OWNER access cannot be revoked — use ownership transfer instead");
        }

        if (!access.isActive()) {
            log.warn("Revoke skipped — property access already inactive — id: {}", id);
            return;
        }

        access.setActive(false);
        propertyAccessRepository.saveAndFlush(access);
        log.info("Property access revoked — id: {}", id);
    }
}
