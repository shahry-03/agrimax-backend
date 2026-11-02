package com.agrimax.identity.service.impl;

import com.agrimax.common.exception.ResourceNotFoundException;
import com.agrimax.identity.config.IdentityProperties;
import com.agrimax.identity.dto.*;
import com.agrimax.identity.entity.RefreshToken;
import com.agrimax.identity.entity.User;
import com.agrimax.identity.repository.UserRepository;
import com.agrimax.identity.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TwoFactorAuthServiceImpl implements TwoFactorAuthService {

    private final UserRepository userRepository;
    private final TotpService totpService;
    private final BackupCodeService backupCodeService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final IdentityProperties properties;

    // ═══════════════════════════════════════════════════════════
    //  SETUP
    // ═══════════════════════════════════════════════════════════

    @Override
    @Transactional
    public TwoFactorSetupResponse setup(User userParam) {
        User user = loadUser(userParam.getId());

        if (user.isTwoFactorEnabled()) {
            throw new IllegalStateException("2FA is already enabled. Disable it first.");
        }

        String secret = totpService.generateSecret();
        user.setTotpSecret(secret);
        userRepository.saveAndFlush(user);

        String label = resolveLabel(user);
        String issuer = properties.getTwoFactor().getIssuer();

        String qrCodeDataUri = totpService.generateQrCodeDataUri(secret, label, issuer);
        String otpAuthUrl = totpService.generateOtpAuthUrl(secret, label, issuer);

        return new TwoFactorSetupResponse(secret, qrCodeDataUri, otpAuthUrl);
    }

    // ═══════════════════════════════════════════════════════════
    //  ENABLE
    // ═══════════════════════════════════════════════════════════

    @Override
    @Transactional
    public TwoFactorEnableResponse enable(User userParam, TwoFactorEnableRequest request) {
        User user = loadUser(userParam.getId());

        if (user.isTwoFactorEnabled()) {
            throw new IllegalStateException("2FA is already enabled");
        }
        String secret = user.getTotpSecret();
        if (secret == null) {
            throw new IllegalStateException("Call /setup first to generate secret");
        }
        if (!totpService.verifyCode(secret, request.code())) {
            throw new BadCredentialsException("Invalid verification code");
        }

        user.setTwoFactorEnabled(true);
        userRepository.saveAndFlush(user);

        int count = properties.getTwoFactor().getBackupCodeCount();
        List<String> backupCodes = backupCodeService.generateCodes(user, count);

        log.info("2FA enabled for user: {}", user.getId());
        return new TwoFactorEnableResponse(true, backupCodes);
    }

    // ═══════════════════════════════════════════════════════════
    //  DISABLE
    // ═══════════════════════════════════════════════════════════

    @Override
    @Transactional
    public void disable(User userParam, TwoFactorDisableRequest request) {
        User user = loadUser(userParam.getId());

        if (!user.isTwoFactorEnabled()) {
            throw new IllegalStateException("2FA is not enabled");
        }
        if (user.getPassword() == null
                || !passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BadCredentialsException("Invalid password");
        }
        if (!totpService.verifyCode(user.getTotpSecret(), request.code())) {
            throw new BadCredentialsException("Invalid verification code");
        }

        user.setTwoFactorEnabled(false);
        user.setTotpSecret(null);
        userRepository.saveAndFlush(user);

        backupCodeService.invalidateAll(user);
        refreshTokenService.revokeAllForUser(user.getId());

        log.info("2FA disabled for user: {}", user.getId());
    }

    // ═══════════════════════════════════════════════════════════
    //  STATUS
    // ═══════════════════════════════════════════════════════════

    @Override
    @Transactional(readOnly = true)
    public TwoFactorStatusResponse getStatus(User userParam) {
        User user = loadUser(userParam.getId());
        return new TwoFactorStatusResponse(
                user.isTwoFactorEnabled(),
                backupCodeService.countRemaining(user));
    }

    // ═══════════════════════════════════════════════════════════
    //  LOGIN STEP 2 — VERIFY
    // ═══════════════════════════════════════════════════════════

    @Override
    @Transactional
    public TokenResponse verifyAndCompleteLogin(TwoFactorVerifyRequest request,
                                                 String ipAddress, String userAgent) {
        String tempToken = request.tempToken();

        if (!jwtService.isTwoFactorTempToken(tempToken)) {
            throw new BadCredentialsException("Invalid or expired temporary token");
        }

        UUID userId;
        try {
            userId = jwtService.getUserId(tempToken);
        } catch (Exception e) {
            throw new BadCredentialsException("Invalid temporary token");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadCredentialsException("User not found"));

        if (!user.isTwoFactorEnabled()) {
            throw new BadCredentialsException("2FA is not enabled for this user");
        }

        String code = request.code().trim();
        boolean verified;
        if (code.matches("^\\d{6}$")) {
            verified = totpService.verifyCode(user.getTotpSecret(), code);
        } else {
            verified = backupCodeService.verifyAndConsume(user, code);
        }
        if (!verified) {
            throw new BadCredentialsException("Invalid 2FA code");
        }

        RefreshToken rt = refreshTokenService.createForUser(user, ipAddress, userAgent);
        String accessToken = jwtService.generateAccessToken(user, rt.getJti());
        String refreshToken = jwtService.generateRefreshToken(user, rt.getJti());

        log.info("2FA login complete for user: {}", user.getId());
        return TokenResponse.of(accessToken, refreshToken,
                jwtService.getAccessExpiryMs(), user);
    }

    // ═══════════════════════════════════════════════════════════
    //  Helpers
    // ═══════════════════════════════════════════════════════════

    private User loadUser(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private String resolveLabel(User user) {
        if (user.getEmail() != null) return user.getEmail();
        if (user.getPhoneNumber() != null) return user.getPhoneNumber();
        return user.getId().toString();
    }
}