package com.renterp.domain.auth.service;

import com.renterp.domain.auth.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Password-login lockout. Each method commits on its own, so a failure is
 * recorded even though the login request itself is rejected.
 * After {@code max-failed-attempts} wrong passwords in a row the password
 * login is locked for {@code lock-minutes}; phone OTP login still works.
 */
@Service
public class LoginAttemptService {

    private final UserRepository userRepository;
    private final int maxFailedAttempts;
    private final Duration lockDuration;

    public LoginAttemptService(UserRepository userRepository,
                               @Value("${app.auth.password.max-failed-attempts:5}") int maxFailedAttempts,
                               @Value("${app.auth.password.lock-minutes:15}") long lockMinutes) {
        this.userRepository = userRepository;
        this.maxFailedAttempts = maxFailedAttempts;
        this.lockDuration = Duration.ofMinutes(lockMinutes);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(UUID userId) {
        userRepository.findById(userId).ifPresent(user -> {
            int failed = user.getFailedLoginAttempts() + 1;
            if (failed >= maxFailedAttempts) {
                user.setLoginLockedUntil(Instant.now().plus(lockDuration));
                user.setFailedLoginAttempts(0);
            } else {
                user.setFailedLoginAttempts(failed);
            }
            userRepository.save(user);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccess(UUID userId) {
        userRepository.findById(userId).ifPresent(user -> {
            if (user.getFailedLoginAttempts() != 0 || user.getLoginLockedUntil() != null) {
                user.setFailedLoginAttempts(0);
                user.setLoginLockedUntil(null);
                userRepository.save(user);
            }
        });
    }
}
