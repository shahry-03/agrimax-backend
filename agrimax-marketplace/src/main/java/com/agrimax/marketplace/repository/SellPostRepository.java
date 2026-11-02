package com.agrimax.marketplace.repository;

import com.agrimax.marketplace.entity.SellPost;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SellPostRepository extends JpaRepository<SellPost, UUID> {

    List<SellPost> findBySellerIdOrderByCreatedAtDesc(UUID sellerId);
    List<SellPost> findByStatusOrderByCreatedAtDesc(String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM SellPost p WHERE p.id = :id")
    Optional<SellPost> findByIdForUpdate(@Param("id") UUID id);

    // ── 🆕 Dashboard queries ──
    long countBySellerId(UUID sellerId);
    long countBySellerIdAndStatus(UUID sellerId, String status);
    long countByStatus(String status);
    List<SellPost> findTop5BySellerIdOrderByCreatedAtDesc(UUID sellerId);
}