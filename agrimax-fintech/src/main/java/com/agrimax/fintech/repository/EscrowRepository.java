package com.agrimax.fintech.repository;

import com.agrimax.fintech.entity.Escrow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EscrowRepository extends JpaRepository<Escrow, UUID> {

    Optional<Escrow> findByDealId(UUID dealId);

    // ── 🆕 Admin stats ──
    long countByStatus(String status);

    @Query("""
        SELECT COALESCE(SUM(e.amount), 0) FROM Escrow e
        WHERE e.status = :status
        """)
    BigDecimal sumAmountByStatus(@Param("status") String status);
}