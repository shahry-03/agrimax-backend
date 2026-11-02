package com.agrimax.identity.dto;

public record TwoFactorSetupResponse(
        String secret,         // Base32 — for manual entry
        String qrCodeDataUri,  // data:image/png;base64,...
        String otpAuthUrl      // otpauth:// URI
) {}