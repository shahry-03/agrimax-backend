package com.agrimax.ai.session;

import lombok.Data;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Holds conversational history for a single user session.
 * Stores the last MAX_TURNS (user, assistant) pairs.
 * Sessions expire after EXPIRY_MINUTES of inactivity.
 */
@Data
public class ConversationSession {

    private static final int MAX_TURNS = 5;
    private static final long EXPIRY_MINUTES = 30;

    private final String userId; // "anonymous" for unauthenticated
    private final List<Turn> history = new ArrayList<>();
    private Instant lastActivity = Instant.now();

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

    /** Returns true if this session has expired. */
    public boolean isExpired() {
        return Instant.now().isAfter(lastActivity.plusSeconds(EXPIRY_MINUTES * 60));
    }

    /** Builds Gemini-format contents array from history (excludes last user turn — added separately). */
    public List<Object> buildContents(String currentUserText) {
        List<Object> contents = new ArrayList<>();
        for (Turn turn : history) {
            contents.add(java.util.Map.of(
                    "role", turn.role(),
                    "parts", List.of(java.util.Map.of("text", turn.text()))
            ));
        }
        // Add current user message
        contents.add(java.util.Map.of(
                "role", "user",
                "parts", List.of(java.util.Map.of("text", currentUserText))
        ));
        return contents;
    }

    private void trimHistory() {
        // Keep at most MAX_TURNS * 2 entries (user + model pairs)
        while (history.size() > MAX_TURNS * 2) {
            history.remove(0);
        }
    }

    /** A single conversation turn. */
    public record Turn(String role, String text) {}
}
