package com.agrimax.identity.dto;

import com.agrimax.identity.entity.User;

import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String phoneNumber,
        String name,
        String role,
        String preferredMode,
        String languagePref,
        boolean emailVerified,
        boolean enabled,
        boolean twoFactorEnabled
) {
    public static UserResponse from(User u) {
        return new UserResponse(
                u.getId(), u.getEmail(), u.getPhoneNumber(), u.getName(), u.getRole(),
                u.getPreferredMode() != null ? u.getPreferredMode().name() : null,
                u.getLanguagePref(), u.isEmailVerified(), u.isEnabled(), u.isTwoFactorEnabled());
    }
}