package com.agrimax.identity.service;

import com.agrimax.identity.entity.OtpToken;
import com.agrimax.identity.entity.User;
import com.agrimax.identity.repository.OtpTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Random;

/**
 * Mock OTP service — logs OTP to console instead of sending SMS.
 * Replace with real SMS gateway (Twilio, Zong API) for production.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OtpService {

    private final OtpTokenRepository otpTokenRepository;
    private final Random random = new Random();

    @Transactional
    public OtpToken generateAndSendOtp(User user) {
        String otpCode = String.format("%06d", random.nextInt(999999));

        OtpToken otpToken = OtpToken.builder()
                .user(user)
                .otpCode(otpCode)
                .expiresAt(Instant.now().plus(5, ChronoUnit.MINUTES))
                .verified(false)
                .build();

        otpToken = otpTokenRepository.save(otpToken);

        // ── MOCK: Log OTP instead of sending SMS ──
        log.info("═══════════════════════════════════════");
        log.info("  📱 OTP for {}: {}", user.getPhoneNumber(), otpCode);
        log.info("═══════════════════════════════════════");

        return otpToken;
    }

    @Transactional
    public boolean verifyOtp(String phoneNumber, String otpCode) {
        return otpTokenRepository
                .findTopByUser_PhoneNumberAndVerifiedFalseOrderByCreatedAtDesc(phoneNumber)
                .filter(token -> token.getExpiresAt().isAfter(Instant.now()))
                .filter(token -> token.getOtpCode().equals(otpCode))
                .map(token -> {
                    token.setVerified(true);
                    otpTokenRepository.save(token);
                    return true;
                })
                .orElse(false);
    }
}
