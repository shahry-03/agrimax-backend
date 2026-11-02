package com.agrimax.ai.service;

import com.agrimax.common.voice.CommandHandler;
import com.agrimax.common.voice.CommandResult;
import com.agrimax.common.voice.VoiceIntent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CommandRouterServiceTest {

    private CommandRouterService routerService;

    static class DummyHelpHandler implements CommandHandler {
        @Override
        public String supportedIntent() {
            return "APP_HELP";
        }

        @Override
        public CommandResult handle(Map<String, Object> parameters, UUID userId) {
            return CommandResult.ok("Help message", "مدد کا پیغام");
        }
    }

    static class DuplicateHelpHandler implements CommandHandler {
        @Override
        public String supportedIntent() {
            return "APP_HELP";
        }

        @Override
        public CommandResult handle(Map<String, Object> parameters, UUID userId) {
            return CommandResult.ok("Duplicate help", "مدد");
        }
    }

    @BeforeEach
    void setUp() {
        // Test that duplicate handler does NOT throw IllegalStateException due to collision merge operator
        routerService = new CommandRouterService(List.of(new DummyHelpHandler(), new DuplicateHelpHandler()));
    }

    @Test
    void testRouteSuccessful() {
        VoiceIntent intent = new VoiceIntent("APP_HELP", Map.of(), "en", 0.95);
        CommandResult result = routerService.route(intent, UUID.randomUUID());

        assertNotNull(result);
        assertTrue(result.success());
        assertEquals("Help message", result.messageEn());
    }

    @Test
    void testRouteUnknownIntentReturnsHelpMessage() {
        VoiceIntent intent = new VoiceIntent("NON_EXISTENT_INTENT", Map.of(), "en", 0.90);
        CommandResult result = routerService.route(intent, UUID.randomUUID());

        assertNotNull(result);
        assertTrue(result.success());
        assertTrue(result.messageEn().contains("didn't understand that"));
    }

    @Test
    void testRouteSensitiveIntentBlocksUnauthenticatedUser() {
        // CREATE_SELL_POST requires authentication
        VoiceIntent intent = new VoiceIntent("CREATE_SELL_POST", Map.of(), "en", 0.95);
        CommandResult result = routerService.route(intent, null); // unauthenticated

        assertNotNull(result);
        assertFalse(result.success());
        assertTrue(result.messageEn().contains("Please login first"));
    }
}
