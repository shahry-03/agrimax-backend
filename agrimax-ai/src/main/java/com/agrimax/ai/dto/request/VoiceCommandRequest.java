package com.agrimax.ai.dto.request;

import jakarta.validation.constraints.NotBlank;

public record VoiceCommandRequest(
        @NotBlank(message = "Text transcript is required")
        String transcript,

        // Frontend generates a unique sessionId per user session
        String sessionId
) {}