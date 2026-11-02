package com.agrimax.ai.handler;

import com.agrimax.common.voice.CommandHandler;
import com.agrimax.common.voice.CommandResult;
import com.agrimax.common.voice.UIAction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Handles STOP_LISTENING intent — user wants to end the voice session.
 * "bas", "ruko", "band karo", "stop", "enough", "bye".
 */
@Slf4j
@Component
public class StopListeningHandler implements CommandHandler {

    @Override
    public String supportedIntent() {
        return "STOP_LISTENING";
    }

    @Override
    public CommandResult handle(Map<String, Object> parameters, UUID userId) {
        log.info("User requested to stop listening");
        return CommandResult.okWithAction(
                "Goodbye! Come back anytime. اپنی فصلوں کا خیال رکھیں۔",
                "خدا حافظ! جب چاہیں واپس آئیں۔",
                UIAction.stopListening());
    }
}