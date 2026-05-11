package com.agrimax.common.voice;

/**
 * Result of executing a voice command through a CommandHandler.
 *
 * @param success   Whether the command executed successfully
 * @param messageEn English response message (farmer-friendly)
 * @param messageUr Urdu response message (farmer-friendly)
 * @param data      Optional data payload
 */
public record CommandResult(
        boolean success,
        String messageEn,
        String messageUr,
        Object data
) {
    public static CommandResult ok(String messageEn, String messageUr, Object data) {
        return new CommandResult(true, messageEn, messageUr, data);
    }

    public static CommandResult ok(String messageEn, String messageUr) {
        return new CommandResult(true, messageEn, messageUr, null);
    }

    public static CommandResult fail(String messageEn, String messageUr) {
        return new CommandResult(false, messageEn, messageUr, null);
    }
}
