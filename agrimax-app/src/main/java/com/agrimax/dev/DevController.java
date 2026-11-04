package com.agrimax.dev;

import com.agrimax.common.dto.ApiResponse;
import com.agrimax.common.event.UserRegisteredEvent;
import com.agrimax.fintech.entity.Wallet;
import com.agrimax.fintech.service.WalletService;
import com.agrimax.identity.entity.User;
import com.agrimax.identity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.Map;
import java.util.UUID;

/**
 * ⚠️ DEV-ONLY endpoints. NOT registered in prod profile.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/dev")
@Profile("!prod")
@RequiredArgsConstructor
public class DevController {

    private final WalletService walletService;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    @PostMapping("/wallet/topup")
    public ResponseEntity<ApiResponse<Wallet>> topUp(
            @RequestBody Map<String, Object> body, Principal principal) {

        UUID userId = UUID.fromString(principal.getName());
        BigDecimal amount = new BigDecimal(body.get("amount").toString());

        // Ensure wallet exists (in case event was missed)
        walletService.getOrCreateWallet(userId);
        walletService.credit(userId, amount, "Dev top-up", null, null);

        Wallet wallet = walletService.getWallet(userId);
        log.warn("⚠️ DEV TOP-UP: user={} +{} (balance={})",
                userId, amount, wallet.getBalance());

        return ResponseEntity.ok(ApiResponse.success(
                wallet, "Topped up (DEV ONLY)", "ٹاپ اپ (صرف ڈویلپمنٹ)"));
    }

    /**
     * 🆕 Dev email verification — mimics real flow (publishes event for wallet creation).
     */
    @PostMapping("/users/verify-email")
    public ResponseEntity<ApiResponse<Void>> devVerifyEmail(@RequestParam String email) {
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            return ResponseEntity.ok(ApiResponse.error(
                    "NOT_FOUND", "User not found", "صارف نہیں ملا"));
        }

        boolean wasUnverified = !user.isEmailVerified();
        user.setEmailVerified(true);
        userRepository.save(user);

        // ⚡ Publish event ONLY on first verification (creates wallet + welcome notif)
        if (wasUnverified) {
            log.warn("⚠️ DEV VERIFY: publishing UserRegisteredEvent for {}", user.getId());
            eventPublisher.publishEvent(
                    new UserRegisteredEvent(user.getId(), user.getPhoneNumber(), user.getRole()));
        }

        log.warn("⚠️ DEV EMAIL VERIFY: {} (wasUnverified={})", email, wasUnverified);
        return ResponseEntity.ok(ApiResponse.success(
                "Email verified (DEV) — wallet created if first time",
                "ای میل تصدیق (ڈویلپمنٹ)"));
    }
}