package com.agrimax.identity.dto;

import jakarta.validation.constraints.NotBlank;

public record TwoFactorVerifyRequest(
        @NotBlank String tempToken,
        @NotBlank String code        // 6-digit TOTP or XXXX-XXXX-XXXX backup
) {}