package com.agrimax.identity.dto;

public record TwoFactorStatusResponse(
        boolean enabled,
        long backupCodesRemaining
) {}