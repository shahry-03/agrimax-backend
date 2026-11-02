package com.agrimax.fintech.service;

import com.agrimax.common.exception.BadRequestException;
import com.agrimax.common.exception.ResourceNotFoundException;
import com.agrimax.fintech.entity.Escrow;
import com.agrimax.fintech.repository.EscrowRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Escrow lifecycle manager.
 * Holds buyer's money until deal completes, then releases to seller.
 * All methods are transactional — atomicity guaranteed by caller's @Transactional.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EscrowService {

    private final EscrowRepository escrowRepository;
    private final WalletService walletService;

    /**
     * Debit buyer wallet + create escrow record (HELD).
     * If buyer has insufficient balance → BadRequestException → caller's tx rolls back.
     */
    @Transactional
    public Escrow holdFunds(UUID dealId, UUID buyerId, UUID sellerId, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Escrow amount must be greater than 0");
        }

        // 1. Debit buyer
        walletService.debit(buyerId, amount,
                "Escrow hold for deal " + dealId, dealId, "DEAL");

        // 2. Create escrow
        Escrow escrow = Escrow.builder()
                .dealId(dealId)
                .buyerId(buyerId)
                .sellerId(sellerId)
                .amount(amount)
                .status("HELD")
                .build();

        escrow = escrowRepository.save(escrow);
        log.info("🔒 Escrow held: deal={} buyer={} amount={}",
                dealId, buyerId, amount);
        return escrow;
    }

    /**
     * Release escrow → credit seller wallet.
     */
    @Transactional
    public Escrow releaseFunds(UUID dealId) {
        Escrow escrow = escrowRepository.findByDealId(dealId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Escrow not found for deal: " + dealId));

        if (!"HELD".equals(escrow.getStatus())) {
            throw new BadRequestException(
                    "Escrow is " + escrow.getStatus() + ", cannot release");
        }

        walletService.credit(escrow.getSellerId(), escrow.getAmount(),
                "Escrow release for deal " + dealId, dealId, "DEAL");

        escrow.setStatus("RELEASED");
        escrow.setReleasedAt(Instant.now());
        escrow = escrowRepository.save(escrow);

        log.info("💰 Escrow released: deal={} seller={} amount={}",
                dealId, escrow.getSellerId(), escrow.getAmount());
        return escrow;
    }

    /**
     * Refund escrow → credit buyer wallet back.
     */
    @Transactional
    public Escrow refund(UUID dealId) {
        Escrow escrow = escrowRepository.findByDealId(dealId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Escrow not found for deal: " + dealId));

        if (!"HELD".equals(escrow.getStatus())) {
            throw new BadRequestException(
                    "Escrow is " + escrow.getStatus() + ", cannot refund");
        }

        walletService.credit(escrow.getBuyerId(), escrow.getAmount(),
                "Escrow refund for deal " + dealId, dealId, "DEAL");

        escrow.setStatus("REFUNDED");
        escrow.setReleasedAt(Instant.now());
        escrow = escrowRepository.save(escrow);

        log.info("↩️ Escrow refunded: deal={} buyer={} amount={}",
                dealId, escrow.getBuyerId(), escrow.getAmount());
        return escrow;
    }

    @Transactional(readOnly = true)
    public Escrow getEscrowForDeal(UUID dealId) {
        return escrowRepository.findByDealId(dealId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Escrow not found for deal: " + dealId));
    }
}