package com.agrimax.identity.service;

import com.agrimax.identity.dto.*;
import com.agrimax.identity.entity.User;

public interface TwoFactorAuthService {

    TwoFactorSetupResponse setup(User user);
    TwoFactorEnableResponse enable(User user, TwoFactorEnableRequest request);
    void disable(User user, TwoFactorDisableRequest request);
    TwoFactorStatusResponse getStatus(User user);

    /**
     * Step 2 of login — verify TOTP/backup code, return full tokens.
     * IP/UA captured for session tracking.
     */
    TokenResponse verifyAndCompleteLogin(TwoFactorVerifyRequest request,
                                         String ipAddress, String userAgent);
}