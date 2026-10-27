package com.renterp.domain.auth.service;

import com.renterp.common.exception.DuplicateResourceException;
import com.renterp.common.exception.ResourceNotFoundException;
import com.renterp.domain.auth.dto.CreateUserRequest;
import com.renterp.domain.auth.dto.UpdateUserRequest;
import com.renterp.domain.auth.dto.UserResponse;
import com.renterp.domain.auth.entity.User;
import com.renterp.domain.auth.repository.UserRepository;
import com.renterp.domain.auth.repository.UserSessionRepository;
import com.renterp.domain.notification.repository.DeviceTokenRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Admin-facing user records (/api/v1/users). Self-service lives in
 * {@link AccountService} (/api/v1/me): a user deletes their own account there,
 * with its checks (H3). Phone numbers are never written to the log (C10).
 */
@Service
public class UserService {

    private static final Logger log = LogManager.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final UserSessionRepository sessionRepository;
    private final DeviceTokenRepository deviceTokens;

    public UserService(UserRepository userRepository, UserSessionRepository sessionRepository,
                       DeviceTokenRepository deviceTokens) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.deviceTokens = deviceTokens;
    }

    // ── Create (admin) ─────────────────────────────────────────────────────────

    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        if (userRepository.existsByPhone(request.getPhone())) {
            throw new DuplicateResourceException("User", "phone", "(masked)");
        }

        User user = User.builder()
                .phone(request.getPhone())
                .name(request.getName())
                .role(request.getRole())
                .preferredLanguage(request.getPreferredLanguage())
                .build();

        User saved = userRepository.save(user);
        log.info("User created by admin — id: {}, role: {}", saved.getId(), saved.getRole());
        return UserResponse.from(saved);
    }

    // ── Read ───────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public UserResponse getUserById(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
        return UserResponse.from(user);
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> getAllUsers(Pageable pageable) {
        return userRepository.findAll(pageable).map(UserResponse::from);
    }

    // ── Update (self or admin) ─────────────────────────────────────────────────

    @Transactional
    public UserResponse updateUser(UUID id, UpdateUserRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));

        if (request.getName() != null) {
            user.setName(request.getName());
        }
        if (request.getPreferredLanguage() != null) {
            user.setPreferredLanguage(request.getPreferredLanguage());
        }
        // fcmToken is no longer stored on the user (M12): push tokens are
        // registered per device with POST /api/v1/me/devices.

        // saveAndFlush (not save) so JPA auditing's @LastModifiedDate is applied
        // before the response DTO is built.
        User updated = userRepository.saveAndFlush(user);
        log.info("User updated — id: {}", updated.getId());
        return UserResponse.from(updated);
    }

    // ── Deactivate (admin only, H3) ────────────────────────────────────────────

    /**
     * Admin deactivation: the account can no longer log in, every session ends
     * at once and its push tokens are removed. Nothing is deleted. A user ending
     * their own account uses DELETE /api/v1/me instead.
     */
    @Transactional
    public void deleteUser(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
        if (!user.isActive()) {
            return;
        }
        user.setActive(false);
        userRepository.saveAndFlush(user);
        int ended = sessionRepository.revokeAllByUserId(user.getId(), Instant.now());
        deviceTokens.deleteByUserId(user.getId());
        log.info("User deactivated by admin — id: {}, sessions ended: {}", id, ended);
    }
}
