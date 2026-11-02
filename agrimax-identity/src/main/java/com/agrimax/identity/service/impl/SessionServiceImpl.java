package com.agrimax.identity.service.impl;

import com.agrimax.identity.dto.SessionResponse;
import com.agrimax.identity.entity.RefreshToken;
import com.agrimax.identity.service.RefreshTokenService;
import com.agrimax.identity.service.SessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SessionServiceImpl implements SessionService {

    private final RefreshTokenService refreshTokenService;

    @Override
    @Transactional(readOnly = true)
    public List<SessionResponse> listSessions(UUID userId, String currentJti) {
        return refreshTokenService.getActiveSessionsForUser(userId).stream()
                .map(rt -> toResponse(rt, currentJti))
                .sorted(Comparator.comparing(SessionResponse::createdAt).reversed())
                .toList();
    }

    @Override
    @Transactional
    public void revokeSession(UUID userId, UUID sessionId, String currentJti) {
        RefreshToken session = refreshTokenService.getSessionForUser(sessionId, userId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Session not found or doesn't belong to you"));
        if (session.isRevoked()) {
            throw new IllegalArgumentException("Session is already revoked");
        }
        if (session.getJti().equals(currentJti)) {
            throw new IllegalArgumentException(
                    "Cannot revoke current session — use /logout instead");
        }
        refreshTokenService.revokeSession(session);
        log.info("Session {} revoked by user {}", sessionId, userId);
    }

    @Override
    @Transactional
    public int revokeAllExceptCurrent(UUID userId, String currentJti) {
        int count = refreshTokenService.revokeAllExcept(userId, currentJti);
        log.info("Revoked {} other sessions for user {}", count, userId);
        return count;
    }

    private SessionResponse toResponse(RefreshToken rt, String currentJti) {
        return new SessionResponse(
                rt.getId(),
                rt.getCreatedAt(),
                rt.getExpiresAt(),
                rt.getLastUsedAt(),
                rt.getIpAddress(),
                rt.getUserAgent(),
                rt.getJti().equals(currentJti));
    }
}