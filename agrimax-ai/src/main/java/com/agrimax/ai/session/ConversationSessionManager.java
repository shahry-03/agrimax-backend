package com.agrimax.ai.session;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages in-memory conversation sessions per userId.
 * Expired sessions are cleaned up every 15 minutes via a scheduled task.
 */
@Slf4j
@Component
public class ConversationSessionManager {

    private final ConcurrentHashMap<String, ConversationSession> sessions = new ConcurrentHashMap<>();

    /**
     * Returns the existing session for the given userId, or creates a new one.
     */
    public ConversationSession getOrCreate(String userId) {
        return sessions.computeIfAbsent(userId, ConversationSession::new);
    }

    /**
     * Clears session for a user (e.g., on logout).
     */
    public void clearSession(String userId) {
        sessions.remove(userId);
        log.info("Cleared conversation session for user: {}", userId);
    }

    /**
     * Scheduled cleanup of expired sessions (runs every 15 minutes).
     */
    @Scheduled(fixedDelay = 900_000)
    public void cleanupExpiredSessions() {
        int before = sessions.size();
        sessions.entrySet().removeIf(e -> e.getValue().isExpired());
        int removed = before - sessions.size();
        if (removed > 0) {
            log.info("Cleaned up {} expired conversation sessions. Active: {}", removed, sessions.size());
        }
    }

    public int activeSessionCount() {
        return sessions.size();
    }
}
