package com.agrimax.dashboard.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Farmer (seller) dashboard — "How are my listings doing? What needs my attention?"
 */
public record FarmerDashboardResponse(
        UUID userId,
        String name,

        // ── My listings ──
        long activePosts,
        long soldPosts,
        long totalPosts,

        // ── Bids on my posts (ACTION ITEMS) ──
        long pendingBidsOnMyPosts,
        long totalBidsOnMyPosts,

        // ── My deals ──
        long pendingDeals,
        long completedDeals,

        // ── Financials ──
        BigDecimal walletBalance,
        BigDecimal totalEarnings,

        // ── Notifications ──
        long unreadNotifications,

        // ── Recent activity ──
        List<RecentPost> recentPosts,
        List<RecentDeal> recentDeals
) {

    public record RecentPost(
            UUID id,
            UUID cropId,
            BigDecimal quantity,
            String unit,
            BigDecimal askingPrice,
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