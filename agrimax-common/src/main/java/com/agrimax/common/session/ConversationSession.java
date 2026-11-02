package com.agrimax.common.session;

import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Holds conversational history for a single user session.
 * Stores the last MAX_TURNS (user, assistant) pairs.
 * Sessions expire after EXPIRY_MINUTES of inactivity.
 *
 * <p>Thread-safe: history is CopyOnWriteArrayList, context is ConcurrentHashMap.</p>
 */
@Data
public class ConversationSession {

    private static final int MAX_TURNS = 5;
    private static final long EXPIRY_MINUTES = 30;

    private final String userId;
    private final List<Turn> history = new CopyOnWriteArrayList<>();  // 🔒 thread-safe
    private final Map<String, Object> context = new ConcurrentHashMap<>();
    private volatile Instant lastActivity = Instant.now();            // 🔒 volatile

    public void addUserTurn(String text) {
        history.add(new Turn("user", text));
        trimHistory();
        lastActivity = Instant.now();
    }

    public void addAssistantTurn(String text) {
        history.add(new Turn("model", text));
        trimHistory();
        lastActivity = Instant.now();
    }

    public boolean isExpired() {
        return Instant.now().isAfter(lastActivity.plusSeconds(EXPIRY_MINUTES * 60));
    }

    public List<Object> buildContents(String currentUserText) {
        List<Object> contents = new java.util.ArrayList<>();
        for (Turn turn : history) {
            contents.add(Map.of(
                    "role", turn.role(),
                    "parts", List.of(Map.of("text", turn.text()))
            ));
        }
        contents.add(Map.of(
                "role", "user",
                "parts", List.of(Map.of("text", currentUserText))
        ));
        return contents;
    }

    /**
     * Trim history safely. CopyOnWriteArrayList makes this safe,
     * but two concurrent calls could over-trim. Use synchronized.
     */
    private synchronized void trimHistory() {
        while (history.size() > MAX_TURNS * 2) {
            history.remove(0);
        }
    }

    public record Turn(String role, String text) {}
}