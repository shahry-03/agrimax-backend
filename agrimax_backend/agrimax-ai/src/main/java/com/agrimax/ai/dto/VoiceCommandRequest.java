package com.agrimax.ai.dto;

import jakarta.validation.constraints.NotBlank;

public record VoiceCommandRequest(
        @NotBlank(message = "Text transcript is required")
        String transcript
) {}
