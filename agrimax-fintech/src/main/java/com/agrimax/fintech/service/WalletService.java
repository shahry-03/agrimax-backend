package com.agrimax.fintech.service;

import com.agrimax.common.exception.ResourceNotFoundException;
import com.agrimax.fintech.entity.Transaction;
import com.agrimax.fintech.entity.Wallet;
import com.agrimax.fintech.repository.TransactionRepository;
import com.agrimax.fintech.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;

    @Transactional
    public Wallet createWallet(UUID userId) {
        return walletRepository.findByUserId(userId)
                .orElseGet(() -> {
                    log.info("Creating wallet for user: {}", userId);
                    return walletRepository.save(Wallet.builder().userId(userId).build());
                });
    }

    @Transactional(readOnly = true)
    public Wallet getWallet(UUID userId) {
        return walletRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not found for user: " + userId));
    }

    @Transactional(readOnly = true)
    public List<Transaction> getTransactions(UUID userId) {
        return transactionRepository.findByWallet_UserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional
    public Transaction credit(UUID userId, BigDecimal amount, String description, UUID refId, String refType) {
        Wallet wallet = getWallet(userId);
        wallet.setBalance(wallet.getBalance().add(amount));
        walletRepository.save(wallet);

        return transactionRepository.save(Transaction.builder()
                .wallet(wallet).type("CREDIT").amount(amount)
                .description(description).referenceId(refId).referenceType(refType)
                .build());
    }

    @Transactional
    public Wallet getOrCreateWallet(UUID userId) {
        return walletRepository.findByUserId(userId)
                .orElseGet(() -> {
                    log.warn("Wallet not found for user {}, auto-creating", userId);
                    return walletRepository.save(
                            Wallet.builder().userId(userId).build()
                    );
                });
    }
}
