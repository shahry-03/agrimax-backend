package com.agrimax.ai.service;

import com.agrimax.common.voice.CommandHandler;
import com.agrimax.common.voice.CommandResult;
import com.agrimax.common.voice.VoiceIntent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Routes parsed voice intents to the appropriate CommandHandler.
 * Handlers are auto-discovered via Spring DI — each module registers its own.
 *
 * <p>If no handler is registered for an intent, returns a graceful bilingual
 * error message instead of throwing an exception.</p>
 */
@Slf4j
@Service
public class CommandRouterService {

    private final Map<String, CommandHandler> handlers;

    public CommandRouterService(List<CommandHandler> handlerList) {
        this.handlers = handlerList.stream()
                .collect(Collectors.toMap(CommandHandler::supportedIntent, Function.identity()));
        log.info("Registered {} voice command handlers: {}", handlers.size(), handlers.keySet());
    }

    public CommandResult route(VoiceIntent intent, UUID userId) {
        log.info("Routing intent: {} for user: {}", intent.intent(), userId);

        CommandHandler handler = handlers.get(intent.intent());

        if (handler == null) {
            // Graceful fallback instead of throwing UnsupportedIntentException
            log.warn("No handler registered for intent: {}. Returning help message.", intent.intent());

            String rawText = intent.parameters() != null
                    ? (String) intent.parameters().getOrDefault("raw_text", "")
                    : "";

            return CommandResult.ok(
                    "I didn't understand that. Try saying: 'wheat price', 'sell my crop', or 'show wallet'.",
                    "یہ سمجھ نہیں آیا۔ کہیں: 'گندم کی قیمت'، 'فصل بیچنی ہے'، یا 'والیٹ دکھاؤ'"
            );
        }

        return handler.handle(intent.parameters(), userId);
    }
}
