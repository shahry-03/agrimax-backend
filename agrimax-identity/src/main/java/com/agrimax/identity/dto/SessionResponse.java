package com.agrimax.identity.dto;

import java.time.Instant;
import java.util.UUID;

public record SessionResponse(
        UUID id,
        Instant createdAt,
        Instant expiresAt,
        Instant lastUsedAt,
        String ipAddress,
        String userAgent,
        boolean current
) {}