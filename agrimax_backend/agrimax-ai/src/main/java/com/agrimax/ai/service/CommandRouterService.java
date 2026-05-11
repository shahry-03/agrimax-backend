package com.agrimax.ai.service;

import com.agrimax.common.exception.UnsupportedIntentException;
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
            throw new UnsupportedIntentException(intent.intent());
        }

        return handler.handle(intent.parameters(), userId);
    }
}
