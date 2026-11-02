package com.agrimax.marketplace.controller;

import com.agrimax.common.dto.ApiResponse;
import com.agrimax.marketplace.entity.Bid;
import com.agrimax.marketplace.entity.Deal;
import com.agrimax.marketplace.service.BidService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/marketplace")
@RequiredArgsConstructor
public class BidController {

    private final BidService bidService;

    @PostMapping("/posts/{postId}/bids")
    @PreAuthorize("hasRole('BUYER')")   // 🆕 farmer can never bid
    public ResponseEntity<ApiResponse<Bid>> placeBid(
            @PathVariable UUID postId,
            @RequestBody Map<String, Object> body,
            Principal principal) {
        UUID buyerId = UUID.fromString(principal.getName());
        BigDecimal bidPrice = new BigDecimal(body.get("bidPrice").toString());
        String message = (String) body.get("message");
        Bid bid = bidService.placeBid(postId, buyerId, bidPrice, message);
        return ResponseEntity.ok(ApiResponse.success(
                bid, "Bid placed", "بولی لگا دی گئی"));
    }

    @GetMapping("/posts/{postId}/bids")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<List<Bid>>> getBidsForPost(@PathVariable UUID postId) {
        return ResponseEntity.ok(ApiResponse.success(
                bidService.getBidsForPost(postId),
                "Bids loaded", "بولیاں لوڈ ہوگئیں"));
    }

    @GetMapping("/my-bids")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<List<Bid>>> getMyBids(Principal principal) {
        UUID buyerId = UUID.fromString(principal.getName());
        return ResponseEntity.ok(ApiResponse.success(
                bidService.getMyBids(buyerId),
                "Your bids loaded", "آپ کی بولیاں لوڈ ہوگئیں"));
    }

    // 🆕 — Seller accepts a bid → deal created + escrow held
    @PostMapping("/bids/{bidId}/accept")
    @Transactional
    public ResponseEntity<ApiResponse<Deal>> acceptBid(
            @PathVariable UUID bidId, Principal principal) {
        UUID sellerId = UUID.fromString(principal.getName());
        Deal deal = bidService.acceptBid(bidId, sellerId);
        return ResponseEntity.ok(ApiResponse.success(
                deal, "Bid accepted — deal created", "bid قبول — سودا بن گیا"));
    }
}