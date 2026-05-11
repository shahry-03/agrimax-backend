package com.agrimax.fintech.controller;

import com.agrimax.common.dto.ApiResponse;
import com.agrimax.fintech.entity.Transaction;
import com.agrimax.fintech.entity.Wallet;
import com.agrimax.fintech.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/wallet")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    @GetMapping("/balance")
    public ResponseEntity<ApiResponse<Wallet>> getBalance(Principal principal) {
        UUID userId = UUID.fromString(principal.getName());
        return ResponseEntity.ok(ApiResponse.success(
                walletService.getWallet(userId), "Wallet loaded", "والیٹ لوڈ ہوگیا"));
    }

    @GetMapping("/transactions")
    public ResponseEntity<ApiResponse<List<Transaction>>> getTransactions(Principal principal) {
        UUID userId = UUID.fromString(principal.getName());
        return ResponseEntity.ok(ApiResponse.success(
                walletService.getTransactions(userId), "Transactions loaded", "لین دین لوڈ ہوگئے"));
    }
}

