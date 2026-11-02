package com.agrimax.identity.service;

import com.agrimax.identity.entity.User;

public interface AccountLockoutService {

    void checkAccountLocked(User user);
    boolean recordFailedAttempt(User user);
    void resetFailedAttempts(User user);
    void unlockAccount(User user);
}