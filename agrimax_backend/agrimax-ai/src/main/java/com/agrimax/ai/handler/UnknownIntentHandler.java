package com.agrimax.ai.handler;

import com.agrimax.ai.service.GeminiChatService;
import com.agrimax.common.voice.CommandHandler;
import com.agrimax.common.voice.CommandResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Handles UNKNOWN intent — instead of throwing an error, asks Gemini to provide
 * a helpful conversational response in Urdu/English.
 *
 * Example: "What is the weather like today?" → friendly help message
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UnknownIntentHandler implements CommandHandler {

    private final GeminiChatService geminiChatService;

    @Override
    public String supportedIntent() {
        return "UNKNOWN";
    }

    @Override
    public CommandResult handle(Map<String, Object> parameters, UUID userId) {
        String rawText = (String) parameters.getOrDefault("raw_text", "");
        log.info("Handling UNKNOWN intent for text: \"{}\"", rawText);

        String sessionId = userId != null ? userId.toString() : "anonymous";

        try {
            String[] response = geminiChatService.getHelpResponse(rawText, sessionId);
            return CommandResult.ok(response[1], response[0]); // ok(english, urdu)
        } catch (Exception e) {
            log.warn("Gemini help response failed: {}", e.getMessage());
            return CommandResult.ok(
                    "I didn't understand that. Try: 'wheat price', 'sell crop', or 'my wallet'",
                    "یہ سمجھ نہیں آیا۔ کوشش کریں: 'گندم کی قیمت'، 'فصل بیچنی ہے'، یا 'والیٹ دکھاؤ'"
            );
        }
    }
}
