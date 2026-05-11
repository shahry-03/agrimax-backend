package com.agrimax.identity.dto;

import java.util.UUID;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        UUID userId,
        String name,
        String role,
        boolean isNewUser
) {}
