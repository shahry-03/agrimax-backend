package com.agrimax.marketplace.service;

import com.agrimax.common.event.DealCancelledEvent;
import com.agrimax.common.event.DealCompletedEvent;
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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DealService {

    private final DealRepository dealRepository;
    private final SellPostRepository sellPostRepository;
    private final BidRepository bidRepository;
    private final EscrowService escrowService;
    private final ApplicationEventPublisher eventPublisher;

    // ── Queries ──

    @Transactional(readOnly = true)
    public List<Deal> getDealsForUser(UUID userId) {
        return dealRepository.findBySellerIdOrBuyerIdOrderByCreatedAtDesc(userId, userId);
    }

    @Transactional(readOnly = true)
    public Deal getDealById(UUID dealId, UUID userId) {
        Deal deal = dealRepository.findById(dealId)
                .orElseThrow(() -> new ResourceNotFoundException("Deal not found: " + dealId));
        if (!deal.getSellerId().equals(userId) && !deal.getBuyerId().equals(userId)) {
            throw new ResourceNotFoundException("Deal not found");
        }
        return deal;
    }

    // ── Complete deal → release escrow to seller ──

    /**
     * Buyer confirms delivery → escrow released to seller.
     * Can be called by buyer OR seller (typically buyer).
     */
    @Transactional
    public Deal completeDeal(UUID dealId, UUID userId) {
        Deal deal = dealRepository.findById(dealId)
                .orElseThrow(() -> new ResourceNotFoundException("Deal not found: " + dealId));

        if (!deal.getSellerId().equals(userId) && !deal.getBuyerId().equals(userId)) {
            throw new UnauthorizedException("Not your deal");
        }
        if (!"PENDING".equalsIgnoreCase(deal.getStatus())) {
            throw new BadRequestException("Deal is " + deal.getStatus() + ", cannot complete");
        }

        // Release escrow → seller gets paid
        escrowService.releaseFunds(dealId);

        deal.setStatus("COMPLETED");
        deal.setCompletedAt(Instant.now());
        deal = dealRepository.save(deal);

        log.info("✅ Deal completed: {} | seller={} paid {}",
                dealId, deal.getSellerId(), deal.getFinalPrice());

        eventPublisher.publishEvent(new DealCompletedEvent(
                deal.getId(), deal.getSellerId(), deal.getBuyerId(), deal.getFinalPrice()));

        return deal;
    }

    // ── Cancel deal → refund escrow to buyer ──

    @Transactional
    public Deal cancelDeal(UUID dealId, UUID userId) {
        Deal deal = dealRepository.findById(dealId)
                .orElseThrow(() -> new ResourceNotFoundException("Deal not found: " + dealId));

        if (!deal.getSellerId().equals(userId) && !deal.getBuyerId().equals(userId)) {
            throw new UnauthorizedException("Not your deal");
        }
        if (!"PENDING".equalsIgnoreCase(deal.getStatus())) {
            throw new BadRequestException("Deal is " + deal.getStatus() + ", cannot cancel");
        }

        // Refund escrow → buyer gets money back
        escrowService.refund(dealId);

        deal.setStatus("CANCELLED");
        deal = dealRepository.save(deal);

        // Re-list post
        if (deal.getPost() != null) {
            SellPost post = deal.getPost();
            post.setStatus("ACTIVE");
            sellPostRepository.save(post);
        }

        // Reject original bid
        if (deal.getBid() != null) {
            Bid bid = deal.getBid();
            bid.setStatus("REJECTED");
            bidRepository.save(bid);
        }

        log.info("↩️ Deal cancelled: {} | buyer refunded {}",
                dealId, deal.getFinalPrice());

        eventPublisher.publishEvent(new DealCancelledEvent(
                deal.getId(), deal.getSellerId(), deal.getBuyerId(), deal.getFinalPrice()));

        return deal;
    }
}