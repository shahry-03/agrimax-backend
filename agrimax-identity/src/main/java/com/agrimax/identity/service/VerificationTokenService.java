package com.agrimax.identity.service;

import com.agrimax.identity.entity.User;
import com.agrimax.identity.entity.VerificationToken;

public interface VerificationTokenService {

    VerificationToken createEmailVerificationToken(User user);
    VerificationToken createPasswordResetToken(User user);
    VerificationToken validateToken(String token, VerificationToken.TokenType type);
    void markUsed(VerificationToken token);
}