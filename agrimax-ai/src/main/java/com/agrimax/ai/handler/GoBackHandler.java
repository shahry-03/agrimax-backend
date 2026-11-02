package com.agrimax.ai.handler;

import com.agrimax.common.voice.CommandHandler;
import com.agrimax.common.voice.CommandResult;
import com.agrimax.common.voice.UIAction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Handles GO_BACK intent — frontend executes router.back().
 */
@Slf4j
@Component
public class GoBackHandler implements CommandHandler {

    @Override
    public String supportedIntent() {
        return "GO_BACK";
    }

    @Override
    public CommandResult handle(Map<String, Object> parameters, UUID userId) {
        log.info("Go back request");
        return CommandResult.okWithAction(
                "Going back.",
                "واپس جا رہا ہوں۔",
                UIAction.goBack()
        );
    }
}