package com.agrimax.ai.dto;

import jakarta.validation.constraints.NotBlank;

public record VoiceCommandRequest(
        @NotBlank(message = "Text transcript is required")
        String transcript,

        // Frontend generates a unique sessionId (e.g., UUID) per user session
        // Required for multi-step flows like LOGIN → VERIFY_OTP
        String sessionId
) {}