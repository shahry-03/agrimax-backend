package com.agrimax.identity.service;

import com.agrimax.identity.entity.RefreshToken;
import com.agrimax.identity.entity.User;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenService {

    RefreshToken createForUser(User user, String ipAddress, String userAgent);
    RefreshToken createForUser(User user);

    List<RefreshToken> getActiveSessionsForUser(UUID userId);
    Optional<RefreshToken> getSessionForUser(UUID sessionId, UUID userId);

    void revokeSession(RefreshToken session);
    int revokeAllExcept(UUID userId, String currentJti);
    void touchSession(RefreshToken token);

    RefreshToken validateAndGet(String refreshToken);
    RefreshToken rotate(RefreshToken oldToken);

    void revokeByJti(String jti);
    void revokeByValue(String refreshToken);
    void revokeAllForUser(UUID userId);
}