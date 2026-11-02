package com.agrimax.marketplace.repository;

import com.agrimax.marketplace.entity.Deal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Repository
public interface DealRepository extends JpaRepository<Deal, UUID> {

    List<Deal> findBySellerIdOrBuyerIdOrderByCreatedAtDesc(UUID sellerId, UUID buyerId);
    List<Deal> findBySellerIdOrderByCreatedAtDesc(UUID sellerId);
    List<Deal> findByBuyerIdOrderByCreatedAtDesc(UUID buyerId);

    // ── 🆕 Dashboard queries ──
    long countBySellerIdAndStatus(UUID sellerId, String status);
    long countByBuyerIdAndStatus(UUID buyerId, String status);
    long countByStatus(String status);
    List<Deal> findTop5BySellerIdOrBuyerIdOrderByCreatedAtDesc(UUID sellerId, UUID buyerId);

    @Query("""
        SELECT COALESCE(SUM(d.finalPrice), 0) FROM Deal d
        WHERE d.sellerId = :sellerId AND d.status = 'COMPLETED'
        """)
    BigDecimal sumEarningsBySeller(@Param("sellerId") UUID sellerId);

    @Query("""
        SELECT COALESCE(SUM(d.finalPrice), 0) FROM Deal d
        WHERE d.buyerId = :buyerId AND d.status = 'COMPLETED'
        """)
    BigDecimal sumSpentByBuyer(@Param("buyerId") UUID buyerId);

    @Query("""
        SELECT COALESCE(SUM(d.finalPrice), 0) FROM Deal d
        WHERE d.status = 'COMPLETED'
        """)
    BigDecimal sumCompletedDealVolume();
}