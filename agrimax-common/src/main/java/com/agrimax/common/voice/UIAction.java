package com.agrimax.common.voice;

import java.util.Map;

/**
 * UI action for the frontend to auto-execute after a voice response is spoken.
 * Enables hands-free navigation — farmer doesn't need to tap buttons.
 *
 * <p>Example: "Login karo" → NAVIGATE /login → login page opens automatically,
 * then AI speaks "Apna number batao" and waits for the farmer to speak again.</p>
 */
public record UIAction(
        ActionType type,
        String target,
        Map<String, Object> params
) {

    public enum ActionType {
        /** No UI change — just speak the response and keep listening. */
        NONE,

        /** Frontend router.push(target) with params for state. Keeps listening. */
        NAVIGATE,

        /** router.back() — previous page. Keeps listening. */
        GO_BACK,

        /** Cancel current multi-turn flow (e.g., aborted login). Keeps listening. */
        CANCEL,

        /** Stop mic — end of session. Farmer said "bas" / "ruko" / "stop". */
        STOP_LISTENING
    }

    // ── Factory methods ──

    public static UIAction none() {
        return new UIAction(ActionType.NONE, null, Map.of());
    }

    public static UIAction navigate(String target) {
        return new UIAction(ActionType.NAVIGATE, target, Map.of());
    }

    public static UIAction navigate(String target, Map<String, Object> params) {
        return new UIAction(ActionType.NAVIGATE, target,
                params != null ? params : Map.of());
    }

    public static UIAction goBack() {
        return new UIAction(ActionType.GO_BACK, null, Map.of());
    }

    public static UIAction cancel() {
        return new UIAction(ActionType.CANCEL, null, Map.of());
    }

    public static UIAction stopListening() {
        return new UIAction(ActionType.STOP_LISTENING, null, Map.of());
    }
}