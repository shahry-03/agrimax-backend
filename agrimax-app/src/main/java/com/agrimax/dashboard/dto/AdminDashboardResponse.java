package com.agrimax.dashboard.dto;

import java.math.BigDecimal;

/**
 * Admin dashboard — platform health at a glance.
 */
public record AdminDashboardResponse(

        // ── Users ──
        long totalFarmers,
        long totalBuyers,
        long totalAdmins,
        long totalUsers,

        // ── Marketplace ──
        long totalActivePosts,
        long totalSoldPosts,
        long totalPosts,
        long totalBids,

        // ── Deals ──
        long pendingDeals,
        long completedDeals,
        long cancelledDeals,
        long totalDeals,

        // ── Financials ──
        BigDecimal totalWalletBalance,
        BigDecimal totalEscrowHeld,
        BigDecimal completedDealVolume,
        long totalTransactions
) {}