package com.agrimax.common.voice;

/**
 * Result of executing a voice command through a CommandHandler.
 *
 * <p>Backward compatible — existing handlers that call {@code ok(msg, msg, data)}
 * or {@code fail(msg, msg)} continue to work unchanged (defaults to
 * {@link UIAction#none()}).</p>
 *
 * @param success   Whether the command executed successfully
 * @param messageEn English response message (farmer-friendly)
 * @param messageUr Urdu response message (farmer-friendly)
 * @param uiAction  Frontend action (navigate/go_back/cancel/stop) — executed automatically
 * @param data      Optional payload (tokens, prices, etc.)
 */
public record CommandResult(
        boolean success,
        String messageEn,
        String messageUr,
        UIAction uiAction,
        Object data
) {

    // ═══════════════════════════════════════════════════════
    // Backward-compatible factories (default → UIAction.none())
    // ═══════════════════════════════════════════════════════

    public static CommandResult ok(String messageEn, String messageUr, Object data) {
        return new CommandResult(true, messageEn, messageUr, UIAction.none(), data);
    }

    public static CommandResult ok(String messageEn, String messageUr) {
        return new CommandResult(true, messageEn, messageUr, UIAction.none(), null);
    }

    public static CommandResult fail(String messageEn, String messageUr) {
        return new CommandResult(false, messageEn, messageUr, UIAction.none(), null);
    }

    // ═══════════════════════════════════════════════════════
    // New factories with explicit UIAction
    // ═══════════════════════════════════════════════════════

    public static CommandResult okWithAction(String messageEn, String messageUr,
                                             UIAction uiAction, Object data) {
        return new CommandResult(true, messageEn, messageUr,
                uiAction != null ? uiAction : UIAction.none(), data);
    }

    public static CommandResult okWithAction(String messageEn, String messageUr,
                                             UIAction uiAction) {
        return new CommandResult(true, messageEn, messageUr,
                uiAction != null ? uiAction : UIAction.none(), null);
    }

    public static CommandResult failWithAction(String messageEn, String messageUr,
                                               UIAction uiAction) {
        return new CommandResult(false, messageEn, messageUr,
                uiAction != null ? uiAction : UIAction.none(), null);
    }
}