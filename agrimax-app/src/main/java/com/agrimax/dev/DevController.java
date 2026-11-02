package com.agrimax.dev;

import com.agrimax.common.dto.ApiResponse;
import com.agrimax.fintech.entity.Wallet;
import com.agrimax.fintech.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.Map;
import java.util.UUID;

/**
 * ⚠️ DEV-ONLY endpoints. NOT registered in prod profile.
 * Use for testing money flow without a real payment gateway.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/dev")
@Profile("!prod")
@RequiredArgsConstructor
public class DevController {

    private final WalletService walletService;

    @PostMapping("/wallet/topup")
    public ResponseEntity<ApiResponse<Wallet>> topUp(
            @RequestBody Map<String, Object> body, Principal principal) {

        UUID userId = UUID.fromString(principal.getName());
        BigDecimal amount = new BigDecimal(body.get("amount").toString());

        walletService.credit(userId, amount,
                "Dev top-up", null, null);

        Wallet wallet = walletService.getWallet(userId);
        log.warn("⚠️ DEV TOP-UP: user={} +{} (balance={})",
                userId, amount, wallet.getBalance());

        return ResponseEntity.ok(ApiResponse.success(
                wallet, "Topped up (DEV ONLY)", "ٹاپ اپ (صرف ڈویلپمنٹ)"));
    }
}