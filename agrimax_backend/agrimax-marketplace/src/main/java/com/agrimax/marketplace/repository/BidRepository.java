package com.agrimax.marketplace.repository;

import com.agrimax.marketplace.entity.Bid;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface BidRepository extends JpaRepository<Bid, UUID> {
    List<Bid> findByPost_IdOrderByBidPriceDesc(UUID postId);
    List<Bid> findByBuyerIdOrderByCreatedAtDesc(UUID buyerId);
}
