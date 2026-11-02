package com.agrimax.identity.service;

import com.agrimax.identity.dto.*;

public interface BuyerAuthService {

    UserResponse register(BuyerRegisterRequest request);

    TokenResponse login(BuyerLoginRequest request, String ipAddress, String userAgent);

    void verifyEmail(String token);

    void resendVerificationEmail(String email);

    void forgotPassword(String email);

    void resetPassword(ResetPasswordRequest request);
}