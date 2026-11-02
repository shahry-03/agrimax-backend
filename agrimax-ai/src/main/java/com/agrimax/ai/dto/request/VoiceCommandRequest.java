package com.agrimax.ai.dto.request;

import jakarta.validation.constraints.NotBlank;

public record VoiceCommandRequest(
        @NotBlank(message = "Text transcript is required")
        String transcript,

        String sessionId,

        // 🆕 — frontend injects the ID of the entity currently being viewed
        String contextPostId,
        String contextBidId,
        String contextDealId
) {}