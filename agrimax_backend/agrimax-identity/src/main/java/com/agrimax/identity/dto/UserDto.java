package com.agrimax.identity.dto;

import java.util.UUID;

public record UserDto(
        UUID id,
        String phoneNumber,
        String name,
        String role,
        String languagePref,
        boolean isKycVerified
) {}
