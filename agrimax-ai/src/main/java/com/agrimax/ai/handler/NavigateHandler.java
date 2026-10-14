package com.agrimax.ai.handler;

import com.agrimax.common.voice.CommandHandler;
import com.agrimax.common.voice.CommandResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Handles NAVIGATE intent — returns action command to frontend to change page.
 *
 * <p>User: "Market kholo" → Returns: {action: "navigate", target: "/marketplace"}</p>
 *
 * <p>The frontend's ActionExecutor reads this and calls router.push(target).</p>
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

        if (target == null || target.isBlank()) {
            return CommandResult.fail(
                    "Which page do you want to open?",
                    "کون سا صفحہ کھولنا ہے؟"
            );
        }

        log.info("Navigate request → {}", target);

        Map<String, Object> data = new HashMap<>();
        data.put("action", "navigate");     // ⭐ Frontend will read this
        data.put("target", target);
        data.put("label", pageLabel);

        return CommandResult.ok(
                "Opening " + pageLabel + ".",
                "کھول رہا ہوں " + pageLabel + "۔",
                data
        );
    }
}