package com.agrimax.identity.dto;

import java.util.List;

/** ⚠️ Backup codes shown ONCE — user must save them. */
public record TwoFactorEnableResponse(
        boolean enabled,
        List<String> backupCodes
) {}