package com.agrimax.identity.dto;

import com.agrimax.identity.entity.User;

import java.util.UUID;

public record TokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        Long expiresIn,
        UUID userId,
        String email,
        String phoneNumber,
        String name,
        String role,
        String preferredMode,
        boolean emailVerified,
        boolean requiresTwoFactor,
        String tempToken
) {
    public static TokenResponse of(String access, String refresh, long expiresIn, User user) {
        return new TokenResponse(
                access, refresh, "Bearer", expiresIn,
                user.getId(), user.getEmail(), user.getPhoneNumber(), user.getName(),
                user.getRole(),
                user.getPreferredMode() != null ? user.getPreferredMode().name() : null,
                user.isEmailVerified(),
                false, null);
    }

    public static TokenResponse requiresTwoFactor(String tempToken) {
        return new TokenResponse(null, null, null, null,
                null, null, null, null, null, null, false, true, tempToken);
    }
}