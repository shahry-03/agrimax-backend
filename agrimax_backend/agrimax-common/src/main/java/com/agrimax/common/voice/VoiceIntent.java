package com.agrimax.common.voice;

import java.util.Map;

/**
 * Represents a parsed voice intent extracted by the AI layer.
 *
 * @param intent     The detected intent code (e.g., CREATE_SELL_POST, GET_CROP_PRICE)
 * @param parameters Extracted parameters from the voice command
 * @param language   Detected language: "ur" or "en"
 * @param confidence AI confidence score from 0.0 to 1.0
 */
public record VoiceIntent(
        String intent,
        Map<String, Object> parameters,
        String language,
        double confidence
) {}
