package com.agrimax.fintech.repository;

import com.agrimax.fintech.entity.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WalletRepository extends JpaRepository<Wallet, UUID> {

    Optional<Wallet> findByUserId(UUID userId);

    // ── 🆕 Admin stats ──
    @Query("SELECT COALESCE(SUM(w.balance), 0) FROM Wallet w WHERE w.isActive = true")
    BigDecimal sumAllActiveBalances();
}