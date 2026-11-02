package com.agrimax.identity.service.impl;

import com.agrimax.common.exception.AccountLockedException;
import com.agrimax.identity.config.IdentityProperties;
import com.agrimax.identity.entity.User;
import com.agrimax.identity.repository.UserRepository;
import com.agrimax.identity.service.AccountLockoutService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountLockoutServiceImpl implements AccountLockoutService {

    private final UserRepository userRepository;
    private final IdentityProperties properties;

    @Override
    public void checkAccountLocked(User user) {
        if (!properties.getLockout().isEnabled()) return;

        if (user.isLockExpired()) {
            log.info("Lock expired for user: {}, auto-unlocking", user.getId());
            clearLock(user);
            return;
        }

        if (user.isLocked()) {
            long minutesLeft = ChronoUnit.MINUTES.between(Instant.now(), user.getLockedUntil());
            throw new AccountLockedException(
                    String.format("Account is locked. Try again in %d minute(s).", minutesLeft),
                    user.getLockedUntil());
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean recordFailedAttempt(User user) {
        if (!properties.getLockout().isEnabled()) return false;

        IdentityProperties.Lockout cfg = properties.getLockout();
        Instant now = Instant.now();

        // Reset counter if stale
        if (user.getLastFailedLogin() != null) {
            long minutesSince = ChronoUnit.MINUTES.between(user.getLastFailedLogin(), now);
            if (minutesSince >= cfg.getResetCounterAfterMinutes()) {
                user.setFailedLoginAttempts(0);
            }
        }

        int attempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(attempts);
        user.setLastFailedLogin(now);

        boolean justLocked = false;
        if (attempts >= cfg.getMaxFailedAttempts()) {
            Instant lockUntil = now.plus(cfg.getLockoutDurationMinutes(), ChronoUnit.MINUTES);
            user.setLockedUntil(lockUntil);
            justLocked = true;
            log.warn("Account LOCKED for user: {} until {}", user.getId(), lockUntil);
        } else {
            log.info("Failed login attempt {} of {} for user: {}",
                    attempts, cfg.getMaxFailedAttempts(), user.getId());
        }

        userRepository.saveAndFlush(user);
        return justLocked;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void resetFailedAttempts(User user) {
        if (user.getFailedLoginAttempts() == 0 && user.getLockedUntil() == null) return;
        clearLock(user);
        log.debug("Reset failed attempts for user: {}", user.getId());
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void unlockAccount(User user) {
        clearLock(user);
        log.info("Account manually UNLOCKED for user: {}", user.getId());
    }

    private void clearLock(User user) {
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setLastFailedLogin(null);
        userRepository.saveAndFlush(user);
    }
}