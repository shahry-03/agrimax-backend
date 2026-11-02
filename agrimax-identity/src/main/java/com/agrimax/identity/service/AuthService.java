package com.agrimax.identity.service;

import com.agrimax.common.event.UserRegisteredEvent;
import com.agrimax.common.exception.BadRequestException;
import com.agrimax.common.exception.UnauthorizedException;
import com.agrimax.identity.dto.AuthResponse;
import com.agrimax.identity.dto.SendOtpRequest;
import com.agrimax.identity.dto.VerifyOtpRequest;
import com.agrimax.identity.entity.User;
import com.agrimax.identity.repository.OtpTokenRepository;
import com.agrimax.identity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final int MAX_OTP_REQUESTS_PER_WINDOW = 3;
    private static final int OTP_WINDOW_MINUTES = 10;

    private final UserRepository userRepository;
    private final OtpTokenRepository otpTokenRepository;    // 🆕
    private final OtpService otpService;
    private final JwtService jwtService;
    private final ApplicationEventPublisher eventPublisher;
    private final RefreshTokenService refreshTokenService;

    @Transactional
    public void sendOtp(SendOtpRequest request) {
        String phone = request.phoneNumber();

        // 🔒 Rate limit: max 3 OTPs per phone per 10 min
        Instant windowStart = Instant.now().minus(OTP_WINDOW_MINUTES, ChronoUnit.MINUTES);
        long recent = otpTokenRepository.countRecentByPhone(phone, windowStart);
        if (recent >= MAX_OTP_REQUESTS_PER_WINDOW) {
            log.warn("OTP rate limit hit for phone: {} ({} in last {}m)",
                    phone, recent, OTP_WINDOW_MINUTES);
            throw new BadRequestException(
                    "Too many OTP requests. Please wait a few minutes.");
        }

        User user = userRepository.findByPhoneNumber(phone)
                .orElseGet(() -> {
                    log.info("New user detected, creating account for: {}", phone);
                    return userRepository.save(
                            User.builder()
                                    .phoneNumber(phone)
                                    .role("FARMER")
                                    .languagePref("ur")
                                    .build()
                    );
                });

        otpService.generateAndSendOtp(user);
    }

    @Transactional
    public AuthResponse verifyOtp(VerifyOtpRequest request) {
        User user = userRepository.findByPhoneNumber(request.phoneNumber())
                .orElseThrow(() -> new BadRequestException("User not found"));

        // 🆕 — detect first successful verification BEFORE marking this OTP verified
        boolean isFirstVerification = !otpTokenRepository.existsByUser_IdAndVerifiedTrue(user.getId());

        boolean valid = otpService.verifyOtp(request.phoneNumber(), request.otp());
        if (!valid) {
            throw new UnauthorizedException("Invalid or expired OTP");
        }

        // 🆕 create DB-backed refresh token (works with common /refresh endpoint)
        var rt = refreshTokenService.createForUser(user);
        String accessToken = jwtService.generateAccessToken(user, rt.getJti());
        String refreshToken = jwtService.generateRefreshToken(user, rt.getJti());

        // 🆕 — fire only on the first successful verification ever
        if (isFirstVerification) {
            log.info("🆕 First-time verification for user {} — publishing UserRegisteredEvent",
                    user.getId());
            eventPublisher.publishEvent(
                    new UserRegisteredEvent(user.getId(), user.getPhoneNumber(), user.getRole())
            );
        }

        return new AuthResponse(
                accessToken,
                refreshToken,
                user.getId(),
                user.getName(),
                user.getRole(),
                isFirstVerification
        );
    }
}