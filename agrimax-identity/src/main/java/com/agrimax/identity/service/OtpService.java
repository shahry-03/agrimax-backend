package com.agrimax.identity.service;

import com.agrimax.identity.entity.OtpToken;
import com.agrimax.identity.entity.User;
import com.agrimax.identity.repository.OtpTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class OtpService {

    private static final int MAX_FAILED_ATTEMPTS = 3;

    private final OtpTokenRepository otpTokenRepository;
    private final SecureRandom random = new SecureRandom();

    /** In-memory failed attempt counter per phone. */
    private final Map<String, AtomicInteger> failedAttempts = new ConcurrentHashMap<>();

    @Transactional
    public OtpToken generateAndSendOtp(User user) {
        String otpCode = String.format("%06d", random.nextInt(1_000_000));

        OtpToken otpToken = OtpToken.builder()
                .user(user)
                .otpCode(otpCode)
                .expiresAt(Instant.now().plus(5, ChronoUnit.MINUTES))
                .verified(false)
                .build();

        otpToken = otpTokenRepository.save(otpToken);

        // Reset failed attempts on new OTP
        failedAttempts.remove(user.getPhoneNumber());

        log.info("═══════════════════════════════════════");
        log.info("  📱 OTP for {}: {}", user.getPhoneNumber(), otpCode);
        log.info("═══════════════════════════════════════");

        return otpToken;
    }

    @Transactional
    public boolean verifyOtp(String phoneNumber, String otpCode) {
        // 🔒 Brute-force guard
        AtomicInteger attempts = failedAttempts.computeIfAbsent(
                phoneNumber, k -> new AtomicInteger(0));

        if (attempts.get() >= MAX_FAILED_ATTEMPTS) {
            log.warn("🚫 OTP brute-force blocked for phone: {} ({} failures)",
                    phoneNumber, attempts.get());
            return false;
        }

        boolean valid = otpTokenRepository
                .findTopByUser_PhoneNumberAndVerifiedFalseOrderByCreatedAtDesc(phoneNumber)
                .filter(token -> token.getExpiresAt().isAfter(Instant.now()))
                .filter(token -> token.getOtpCode().equals(otpCode))
                .map(token -> {
                    token.setVerified(true);
                    otpTokenRepository.save(token);
                    return true;
                })
                .orElse(false);

        if (valid) {
            failedAttempts.remove(phoneNumber);   // reset on success
        } else {
            int count = attempts.incrementAndGet();
            log.warn("❌ Failed OTP attempt #{} for phone: {}", count, phoneNumber);
        }

        return valid;
    }
}