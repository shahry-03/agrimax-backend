package com.agrimax.common.voice;

import java.util.Map;
import java.util.UUID;

/**
 * Strategy interface for handling specific voice command intents.
 * Each module registers its own handlers (e.g., LoginHandler, CreateSellPostHandler).
 * The AI module's CommandRouterService discovers all handlers via Spring DI.
 */
public interface CommandHandler {

    /**
     * @return The intent code this handler supports (e.g., "CREATE_SELL_POST")
     */
    String supportedIntent();

    /**
     * Execute the command with the given parameters.
     *
     * @param parameters Extracted parameters from the voice intent
     * @param userId     The authenticated user's ID (may be null for unauthenticated intents like LOGIN)
     * @return The result of the command execution
     */
    CommandResult handle(Map<String, Object> parameters, UUID userId);
}
