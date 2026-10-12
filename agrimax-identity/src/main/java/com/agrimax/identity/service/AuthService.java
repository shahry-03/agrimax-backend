package com.agrimax.identity.service;

import com.agrimax.common.event.UserRegisteredEvent;
import com.agrimax.common.exception.BadRequestException;
import com.agrimax.common.exception.UnauthorizedException;
import com.agrimax.identity.dto.AuthResponse;
import com.agrimax.identity.dto.SendOtpRequest;
import com.agrimax.identity.dto.VerifyOtpRequest;
import com.agrimax.identity.entity.User;
import com.agrimax.identity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles OTP-based authentication flow.
 * New users are auto-registered on first OTP verification.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final OtpService otpService;
    private final JwtService jwtService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void sendOtp(SendOtpRequest request) {
        User user = userRepository.findByPhoneNumber(request.phoneNumber())
                .orElseGet(() -> {
                    log.info("New user detected, creating account for: {}", request.phoneNumber());
                    return userRepository.save(
                            User.builder()
                                    .phoneNumber(request.phoneNumber())
                                    .role("FARMER")
                                    .languagePref("ur")
                                    .build()
                    );
                });

        otpService.generateAndSendOtp(user);
    }

    @Transactional
    public AuthResponse verifyOtp(VerifyOtpRequest request) {
        boolean valid = otpService.verifyOtp(request.phoneNumber(), request.otp());
        if (!valid) {
            throw new UnauthorizedException("Invalid or expired OTP");
        }

        User user = userRepository.findByPhoneNumber(request.phoneNumber())
                .orElseThrow(() -> new BadRequestException("User not found"));

        boolean isNewUser = user.getName() == null;

        String accessToken = jwtService.generateAccessToken(user.getId(), user.getRole());
        String refreshToken = jwtService.generateRefreshToken(user.getId(), user.getRole());

        // Publish event for other modules (e.g., Fintech creates wallet)
        if (isNewUser) {
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
                isNewUser
        );
    }
}
