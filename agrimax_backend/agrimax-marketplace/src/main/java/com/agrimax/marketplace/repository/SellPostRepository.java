package com.agrimax.marketplace.repository;

import com.agrimax.marketplace.entity.SellPost;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface SellPostRepository extends JpaRepository<SellPost, UUID> {
    List<SellPost> findBySellerIdOrderByCreatedAtDesc(UUID sellerId);
    List<SellPost> findByStatusOrderByCreatedAtDesc(String status);
}
