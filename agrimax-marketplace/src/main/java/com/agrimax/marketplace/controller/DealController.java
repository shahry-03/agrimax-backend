package com.agrimax.marketplace.controller;

import com.agrimax.common.dto.ApiResponse;
import com.agrimax.marketplace.entity.Deal;
import com.agrimax.marketplace.service.DealService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/marketplace/deals")
@RequiredArgsConstructor
public class DealController {

    private final DealService dealService;

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<List<Deal>>> getMyDeals(Principal principal) {
        UUID userId = UUID.fromString(principal.getName());
        return ResponseEntity.ok(ApiResponse.success(
                dealService.getDealsForUser(userId),
                "Deals loaded", "سودے لوڈ ہوگئے"));
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<Deal>> getDeal(
            @PathVariable UUID id, Principal principal) {
        UUID userId = UUID.fromString(principal.getName());
        return ResponseEntity.ok(ApiResponse.success(
                dealService.getDealById(id, userId),
                "Deal loaded", "سودا لوڈ ہوگیا"));
    }

    // 🆕 — Complete deal → escrow released to seller
    @PostMapping("/{id}/complete")
    @Transactional
    public ResponseEntity<ApiResponse<Deal>> completeDeal(
            @PathVariable UUID id, Principal principal) {
        UUID userId = UUID.fromString(principal.getName());
        Deal deal = dealService.completeDeal(id, userId);
        return ResponseEntity.ok(ApiResponse.success(
                deal, "Deal completed — payment released",
                "سودا مکمل — ادائیگی جاری"));
    }

    // 🆕 — Cancel deal → escrow refunded to buyer
    @PostMapping("/{id}/cancel")
    @Transactional
    public ResponseEntity<ApiResponse<Deal>> cancelDeal(
            @PathVariable UUID id, Principal principal) {
        UUID userId = UUID.fromString(principal.getName());
        Deal deal = dealService.cancelDeal(id, userId);
        return ResponseEntity.ok(ApiResponse.success(
                deal, "Deal cancelled — payment refunded",
                "سودا منسوخ — ادائیگی واپس"));
    }
}