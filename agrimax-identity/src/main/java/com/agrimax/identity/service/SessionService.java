package com.agrimax.identity.service;

import com.agrimax.identity.dto.SessionResponse;

import java.util.List;
import java.util.UUID;

public interface SessionService {
    List<SessionResponse> listSessions(UUID userId, String currentJti);
    void revokeSession(UUID userId, UUID sessionId, String currentJti);
    int revokeAllExceptCurrent(UUID userId, String currentJti);
}