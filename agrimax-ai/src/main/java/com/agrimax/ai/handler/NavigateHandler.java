package com.agrimax.ai.handler;

import com.agrimax.common.voice.CommandHandler;
import com.agrimax.common.voice.CommandResult;
import com.agrimax.common.voice.UIAction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Handles NAVIGATE intent — returns a navigation action to the frontend.
 *
 * <p>User: "Market kholo" → Navigates to "/marketplace"</p>
 *
 * <p>The frontend's ActionExecutor reads the UIAction
 * and calls router.push(target).</p>
 */
@Slf4j
@Component
public class NavigateHandler implements CommandHandler {

    @Override
    public String supportedIntent() {
        return "NAVIGATE";
    }

    @Override
    public CommandResult handle(Map<String, Object> parameters, UUID userId) {
        String target = (String) parameters.get("target");
        String pageLabel = (String) parameters.getOrDefault("label", target);

        // Validate navigation target
        if (target == null || target.isBlank()) {
            return CommandResult.fail(
                    "Which page do you want to open?",
                    "کون سا صفحہ کھولنا ہے؟"
            );
        }

        log.info("Navigate request → {}", target);

        // Prepare response data
        Map<String, Object> data = new HashMap<>();
        data.put("target", target);
        data.put("label", pageLabel);

        // Return success with navigation action
        return CommandResult.okWithAction(
                "Opening " + pageLabel + ".",
                "کھول رہا ہوں " + pageLabel + "۔",
                UIAction.navigate(target, Map.of("source", "voice")),
                data
        );
    }
}