package com.agrimax.dashboard.service;

import com.agrimax.common.exception.ResourceNotFoundException;
import com.agrimax.dashboard.dto.*;
import com.agrimax.fintech.entity.Wallet;
import com.agrimax.fintech.repository.EscrowRepository;
import com.agrimax.fintech.repository.TransactionRepository;
import com.agrimax.fintech.repository.WalletRepository;
import com.agrimax.fintech.service.WalletService;
import com.agrimax.identity.entity.User;
import com.agrimax.identity.repository.UserRepository;
import com.agrimax.marketplace.entity.Bid;
import com.agrimax.marketplace.entity.Deal;
import com.agrimax.marketplace.entity.SellPost;
import com.agrimax.marketplace.repository.BidRepository;
import com.agrimax.marketplace.repository.DealRepository;
import com.agrimax.marketplace.repository.SellPostRepository;
import com.agrimax.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final UserRepository userRepository;
    private final SellPostRepository sellPostRepository;
    private final BidRepository bidRepository;
    private final DealRepository dealRepository;
    private final WalletRepository walletRepository;
    private final WalletService walletService;
    private final EscrowRepository escrowRepository;
    private final TransactionRepository transactionRepository;
    private final NotificationRepository notificationRepository;

    // ═══════════════════════════════════════════════════════════
    //  FARMER DASHBOARD
    // ═══════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public FarmerDashboardResponse getFarmerDashboard(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        long activePosts = sellPostRepository.countBySellerIdAndStatus(userId, "ACTIVE");
        long soldPosts = sellPostRepository.countBySellerIdAndStatus(userId, "SOLD");
        long totalPosts = sellPostRepository.countBySellerId(userId);

        long pendingBids = bidRepository.countByPost_SellerIdAndStatus(userId, "PENDING");
        long totalBids = bidRepository.countByPost_SellerId(userId);

        long pendingDeals = dealRepository.countBySellerIdAndStatus(userId, "PENDING");
        long completedDeals = dealRepository.countBySellerIdAndStatus(userId, "COMPLETED");

        BigDecimal walletBalance = resolveBalance(userId);
        BigDecimal totalEarnings = safeAmount(dealRepository.sumEarningsBySeller(userId));

        long unread = notificationRepository.countByUserIdAndIsReadFalse(userId);

        List<SellPost> recentPosts = sellPostRepository
                .findTop5BySellerIdOrderByCreatedAtDesc(userId);
        List<Deal> recentDeals = dealRepository
                .findTop5BySellerIdOrBuyerIdOrderByCreatedAtDesc(userId, userId);

        // Bulk-fetch buyer names for recent deals
        Map<UUID, String> buyerNames = resolveUserNames(
                recentDeals.stream().map(Deal::getBuyerId).collect(Collectors.toSet()));

        return new FarmerDashboardResponse(
                user.getId(),
                displayName(user, "Farmer"),
                activePosts, soldPosts, totalPosts,
                pendingBids, totalBids,
                pendingDeals, completedDeals,
                walletBalance, totalEarnings,
                unread,
                recentPosts.stream().map(this::toRecentPost).toList(),
                recentDeals.stream()
                        .filter(d -> d.getSellerId().equals(userId))
                        .map(d -> toRecentDeal(d, d.getBuyerId(),
                                buyerNames.getOrDefault(d.getBuyerId(), "Buyer")))
                        .toList()
        );
    }

    // ═══════════════════════════════════════════════════════════
    //  BUYER DASHBOARD
    // ═══════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public BuyerDashboardResponse getBuyerDashboard(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        long pendingBids = bidRepository.countByBuyerIdAndStatus(userId, "PENDING");
        long acceptedBids = bidRepository.countByBuyerIdAndStatus(userId, "ACCEPTED");
        long rejectedBids = bidRepository.countByBuyerIdAndStatus(userId, "REJECTED");
        long totalBids = bidRepository.countByBuyerId(userId);

        long pendingDeals = dealRepository.countByBuyerIdAndStatus(userId, "PENDING");
        long completedDeals = dealRepository.countByBuyerIdAndStatus(userId, "COMPLETED");

        BigDecimal walletBalance = resolveBalance(userId);
        BigDecimal totalSpent = safeAmount(dealRepository.sumSpentByBuyer(userId));

        long activePosts = sellPostRepository.countByStatus("ACTIVE");

        long unread = notificationRepository.countByUserIdAndIsReadFalse(userId);

        List<Bid> recentBids = bidRepository
                .findTop5ByBuyerIdOrderByCreatedAtDesc(userId);
        List<Deal> recentDeals = dealRepository
                .findTop5BySellerIdOrBuyerIdOrderByCreatedAtDesc(userId, userId);

        Map<UUID, String> sellerNames = resolveUserNames(
                recentDeals.stream().map(Deal::getSellerId).collect(Collectors.toSet()));

        return new BuyerDashboardResponse(
                user.getId(),
                displayName(user, "Buyer"),
                pendingBids, acceptedBids, rejectedBids, totalBids,
                pendingDeals, completedDeals,
                walletBalance, totalSpent,
                activePosts,
                unread,
                recentBids.stream().map(this::toRecentBid).toList(),
                recentDeals.stream()
                        .filter(d -> d.getBuyerId().equals(userId))
                        .map(d -> toBuyerRecentDeal(d, d.getSellerId(),
                            sellerNames.getOrDefault(d.getSellerId(), "Farmer")))
                        .toList()
        );
    }

    // ═══════════════════════════════════════════════════════════
    //  ADMIN DASHBOARD
    // ═══════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public AdminDashboardResponse getAdminDashboard() {
        long farmers = userRepository.countByRole("FARMER");
        long buyers = userRepository.countByRole("BUYER");
        long admins = userRepository.countByRole("ADMIN");
        long totalUsers = farmers + buyers + admins
                + userRepository.countByRole("DELIVERY");

        long activePosts = sellPostRepository.countByStatus("ACTIVE");
        long soldPosts = sellPostRepository.countByStatus("SOLD");
        long totalPosts = sellPostRepository.count();
        long totalBids = bidRepository.count();

        long pendingDeals = dealRepository.countByStatus("PENDING");
        long completedDeals = dealRepository.countByStatus("COMPLETED");
        long cancelledDeals = dealRepository.countByStatus("CANCELLED");
        long totalDeals = dealRepository.count();

        BigDecimal totalWallet = safeAmount(walletRepository.sumAllActiveBalances());
        BigDecimal escrowHeld = safeAmount(escrowRepository.sumAmountByStatus("HELD"));
        BigDecimal completedVolume = safeAmount(dealRepository.sumCompletedDealVolume());
        long totalTransactions = transactionRepository.count();

        return new AdminDashboardResponse(
                farmers, buyers, admins, totalUsers,
                activePosts, soldPosts, totalPosts, totalBids,
                pendingDeals, completedDeals, cancelledDeals, totalDeals,
                totalWallet, escrowHeld, completedVolume, totalTransactions
        );
    }

    // ═══════════════════════════════════════════════════════════
    //  HELPERS
    // ═══════════════════════════════════════════════════════════

    private BigDecimal resolveBalance(UUID userId) {
        return walletRepository.findByUserId(userId)
                .map(Wallet::getBalance)
                .orElse(BigDecimal.ZERO);
    }

    private BigDecimal safeAmount(BigDecimal amount) {
        return amount != null ? amount : BigDecimal.ZERO;
    }

    private String displayName(User user, String fallback) {
        return (user.getName() != null && !user.getName().isBlank())
                ? user.getName() : fallback;
    }

    private Map<UUID, String> resolveUserNames(Set<UUID> userIds) {
        if (userIds.isEmpty()) return Map.of();
        return userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(
                        User::getId,
                        u -> u.getName() != null && !u.getName().isBlank()
                                ? u.getName() : "User",
                        (a, b) -> a));
    }

    private FarmerDashboardResponse.RecentPost toRecentPost(SellPost p) {
        return new FarmerDashboardResponse.RecentPost(
                p.getId(), p.getCropId(), p.getQuantity(), p.getUnit(),
                p.getAskingPrice(), p.getStatus(),
                p.getCreatedAt() != null ? p.getCreatedAt().toString() : null);
    }

    private BuyerDashboardResponse.RecentBid toRecentBid(Bid b) {
        return new BuyerDashboardResponse.RecentBid(
                b.getId(), b.getPost().getId(), b.getBidPrice(), b.getStatus(),
                b.getCreatedAt() != null ? b.getCreatedAt().toString() : null);
    }

    private FarmerDashboardResponse.RecentDeal toRecentDeal(
            Deal d, UUID counterpartyId, String counterpartyName) {
        return new FarmerDashboardResponse.RecentDeal(
                d.getId(),
                d.getPost() != null ? d.getPost().getId() : null,
                d.getFinalPrice(), d.getStatus(),
                counterpartyId, counterpartyName,
                d.getCreatedAt() != null ? d.getCreatedAt().toString() : null);
    }

    private BuyerDashboardResponse.RecentDeal toBuyerRecentDeal(
            Deal d, UUID counterpartyId, String counterpartyName) {
        return new BuyerDashboardResponse.RecentDeal(
                d.getId(),
                d.getPost() != null ? d.getPost().getId() : null,
                d.getFinalPrice(), d.getStatus(),
                counterpartyId, counterpartyName,
                d.getCreatedAt() != null ? d.getCreatedAt().toString() : null);
    }
}