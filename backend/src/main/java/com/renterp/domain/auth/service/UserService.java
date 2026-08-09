package com.renterp.domain.auth.service;

import com.renterp.common.exception.DuplicateResourceException;
import com.renterp.common.exception.ResourceNotFoundException;
import com.renterp.domain.auth.dto.CreateUserRequest;
import com.renterp.domain.auth.dto.UpdateUserRequest;
import com.renterp.domain.auth.dto.UserResponse;
import com.renterp.domain.auth.entity.User;
import com.renterp.domain.auth.repository.UserRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UserService {

    private static final Logger log = LogManager.getLogger(UserService.class);

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // ── Create ─────────────────────────────────────────────────────────────────

    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        log.debug("Creating user with phone: {}", request.getPhone());

        if (userRepository.existsByPhone(request.getPhone())) {
            log.warn("User creation failed — phone already registered: {}", request.getPhone());
            throw new DuplicateResourceException("User", "phone", request.getPhone());
        }

        User user = User.builder()
                .phone(request.getPhone())
                .name(request.getName())
                .role(request.getRole())
                .preferredLanguage(request.getPreferredLanguage())
                .build();

        User saved = userRepository.save(user);
        log.info("User created successfully — id: {}, phone: {}, role: {}", saved.getId(), saved.getPhone(), saved.getRole());

        return UserResponse.from(saved);
    }

    // ── Read single ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public UserResponse getUserById(UUID id) {
        log.debug("Fetching user by id: {}", id);

        User user = userRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("User not found — id: {}", id);
                    return new ResourceNotFoundException("User", "id", id);
                });

        log.debug("User fetched — id: {}, phone: {}", user.getId(), user.getPhone());
        return UserResponse.from(user);
    }

    // ── Read all (paginated) ───────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<UserResponse> getAllUsers(Pageable pageable) {
        log.debug("Fetching all users — page: {}, size: {}", pageable.getPageNumber(), pageable.getPageSize());

        Page<UserResponse> page = userRepository.findAll(pageable)
                .map(UserResponse::from);

        log.debug("Users fetched — total: {}, page: {}/{}", page.getTotalElements(), page.getNumber() + 1, page.getTotalPages());
        return page;
    }

    // ── Update ─────────────────────────────────────────────────────────────────

    @Transactional
    public UserResponse updateUser(UUID id, UpdateUserRequest request) {
        log.debug("Updating user — id: {}", id);

        User user = userRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Update failed — user not found — id: {}", id);
                    return new ResourceNotFoundException("User", "id", id);
                });

        if (request.getName() != null) {
            user.setName(request.getName());
        }
        if (request.getPreferredLanguage() != null) {
            user.setPreferredLanguage(request.getPreferredLanguage());
        }
        if (request.getFcmToken() != null) {
            user.setFcmToken(request.getFcmToken());
        }

        // saveAndFlush (not save) so JPA auditing's @LastModifiedDate is applied
        // to the managed entity BEFORE we build the response DTO. With a plain
        // save() the flush happens at commit, after the DTO is built, and the
        // response would carry a stale updatedAt.
        User updated = userRepository.saveAndFlush(user);
        log.info("User updated successfully — id: {}", updated.getId());

        return UserResponse.from(updated);
    }

    // ── Soft Delete ────────────────────────────────────────────────────────────

    @Transactional
    public void deleteUser(UUID id) {
        log.debug("Soft deleting user — id: {}", id);

        User user = userRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Delete failed — user not found — id: {}", id);
                    return new ResourceNotFoundException("User", "id", id);
                });

        if (!user.isActive()) {
            log.warn("Delete skipped — user already inactive — id: {}", id);
            return;
        }

        user.setActive(false);
        userRepository.saveAndFlush(user);
        log.info("User soft deleted — id: {}", id);
    }
}
