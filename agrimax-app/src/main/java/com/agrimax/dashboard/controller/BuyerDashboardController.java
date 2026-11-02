package com.agrimax.dashboard.controller;

import com.agrimax.common.dto.ApiResponse;
import com.agrimax.dashboard.dto.BuyerDashboardResponse;
import com.agrimax.dashboard.service.DashboardService;
import com.agrimax.identity.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/buyer")
@RequiredArgsConstructor
public class BuyerDashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<BuyerDashboardResponse>> getDashboard(
            @AuthenticationPrincipal User user) {
        BuyerDashboardResponse data = dashboardService.getBuyerDashboard(user.getId());
        return ResponseEntity.ok(ApiResponse.success(
                data,
                "Buyer dashboard loaded",
                "خریدار ڈیش بورڈ لوڈ ہو گیا"));
    }
}