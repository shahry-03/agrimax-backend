package com.agrimax.marketplace.service;

import com.agrimax.common.event.BidAcceptedEvent;
import com.agrimax.common.event.BidPlacedEvent;
import com.agrimax.common.exception.BadRequestException;
import com.agrimax.common.exception.ResourceNotFoundException;
import com.agrimax.common.exception.UnauthorizedException;
import com.agrimax.fintech.service.EscrowService;
import com.agrimax.marketplace.entity.Bid;
import com.agrimax.marketplace.entity.Deal;
import com.agrimax.marketplace.entity.SellPost;
import com.agrimax.marketplace.repository.BidRepository;
import com.agrimax.marketplace.repository.DealRepository;
import com.agrimax.marketplace.repository.SellPostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BidService {

    private final BidRepository bidRepository;
    private final SellPostRepository sellPostRepository;
    private final DealRepository dealRepository;
    private final EscrowService escrowService;
    private final ApplicationEventPublisher eventPublisher;

    // ── Place bid ──

    @Transactional
    public Bid placeBid(UUID postId, UUID buyerId, BigDecimal bidPrice, String message) {
        if (postId == null) throw new BadRequestException("Post ID is required");
        if (buyerId == null) throw new BadRequestException("Buyer must be logged in");
        if (bidPrice == null || bidPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Bid price must be greater than 0");
        }

        SellPost post = sellPostRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found: " + postId));

        if (!"ACTIVE".equalsIgnoreCase(post.getStatus())) {
            throw new BadRequestException("This post is no longer active");
        }
        if (post.getExpiresAt() != null && post.getExpiresAt().isBefore(Instant.now())) {
            throw new BadRequestException("This post has expired");
        }
        if (post.getSellerId().equals(buyerId)) {
            throw new BadRequestException("You cannot bid on your own post");
        }

        Bid bid = Bid.builder()
                .post(post).buyerId(buyerId).bidPrice(bidPrice)
                .message(message).status("PENDING").build();

        bid = bidRepository.save(bid);
        log.info("Bid placed: {} on post {} by buyer {}", bid.getId(), postId, buyerId);

        eventPublisher.publishEvent(new BidPlacedEvent(
                bid.getId(), postId, buyerId, post.getSellerId(), bidPrice));

        return bid;
    }

    // ── Accept bid → create deal + hold escrow ──

    /**
     * Seller accepts a bid. Atomically:
     * 1. Locks post row (prevents double-accept)
     * 2. Creates Deal
     * 3. Holds escrow (debits buyer wallet)
     * 4. Marks post SOLD, bid ACCEPTED, other bids REJECTED
     */
    @Transactional
    public Deal acceptBid(UUID bidId, UUID sellerId) {
        Bid bid = bidRepository.findById(bidId)
                .orElseThrow(() -> new ResourceNotFoundException("Bid not found: " + bidId));

        UUID postId = bid.getPost().getId();

        // Pessimistic lock — safe against concurrent accepts
        SellPost post = sellPostRepository.findByIdForUpdate(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found: " + postId));

        // Validations
        if (!post.getSellerId().equals(sellerId)) {
            throw new UnauthorizedException("This is not your post");
        }
        if (!"ACTIVE".equalsIgnoreCase(post.getStatus())) {
            throw new BadRequestException("Post is " + post.getStatus() + ", cannot accept bid");
        }
        if (!"PENDING".equalsIgnoreCase(bid.getStatus())) {
            throw new BadRequestException("Bid is " + bid.getStatus() + ", cannot accept");
        }

        // 1. Create deal first (escrow needs dealId)
        Deal deal = Deal.builder()
                .post(post)
                .bid(bid)
                .sellerId(sellerId)
                .buyerId(bid.getBuyerId())
                .finalPrice(bid.getBidPrice())
                .quantity(post.getQuantity())
                .status("PENDING")
                .build();
        deal = dealRepository.save(deal);

        // 2. Hold escrow (debits buyer)
        escrowService.holdFunds(
                deal.getId(), deal.getBuyerId(), deal.getSellerId(), deal.getFinalPrice());

        // 3. Update states
        post.setStatus("SOLD");
        sellPostRepository.save(post);

        bid.setStatus("ACCEPTED");
        bidRepository.save(bid);

        // 4. Reject other pending bids
        List<Bid> allBids = bidRepository.findByPost_IdOrderByBidPriceDesc(postId);
        for (Bid b : allBids) {
            if (!b.getId().equals(bidId) && "PENDING".equalsIgnoreCase(b.getStatus())) {
                b.setStatus("REJECTED");
                bidRepository.save(b);
            }
        }

        log.info("✅ Deal created: {} | post={} | bid={} | seller={} | buyer={} | amount={}",
                deal.getId(), postId, bidId, sellerId, bid.getBuyerId(), deal.getFinalPrice());

        eventPublisher.publishEvent(new BidAcceptedEvent(
                bidId, deal.getId(), sellerId, bid.getBuyerId(), bid.getBidPrice()));

        return deal;
    }

    // ── Queries ──

    @Transactional(readOnly = true)
    public List<Bid> getBidsForPost(UUID postId) {
        return bidRepository.findByPost_IdOrderByBidPriceDesc(postId);
    }

    @Transactional(readOnly = true)
    public List<Bid> getMyBids(UUID buyerId) {
        return bidRepository.findByBuyerIdOrderByCreatedAtDesc(buyerId);
    }
}