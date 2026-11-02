package com.agrimax.dashboard.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Buyer dashboard — "What's the status of my bids? How much did I spend?"
 */
public record BuyerDashboardResponse(
        UUID userId,
        String name,

        // ── My bids ──
        long pendingBids,
        long acceptedBids,
        long rejectedBids,
        long totalBids,

        // ── My deals ──
        long pendingDeals,
        long completedDeals,

        // ── Financials ──
        BigDecimal walletBalance,
        BigDecimal totalSpent,

        // ── Marketplace ──
        long activeMarketplacePosts,

        // ── Notifications ──
        long unreadNotifications,

        // ── Recent activity ──
        List<RecentBid> recentBids,
        List<RecentDeal> recentDeals
) {

    public record RecentBid(
            UUID id,
            UUID postId,
            BigDecimal bidPrice,
            String status,
            String createdAt
    ) {}

    public record RecentDeal(
            UUID id,
            UUID postId,
            BigDecimal amount,
            String status,
            UUID counterpartyId,
            String counterpartyName,
            String createdAt
    ) {}
}