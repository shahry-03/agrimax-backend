package com.agrimax.dashboard.controller;

import com.agrimax.common.dto.ApiResponse;
import com.agrimax.dashboard.dto.FarmerDashboardResponse;
import com.agrimax.dashboard.service.DashboardService;
import com.agrimax.identity.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/farmer")
@RequiredArgsConstructor
public class FarmerDashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<FarmerDashboardResponse>> getDashboard(
            @AuthenticationPrincipal User user) {
        FarmerDashboardResponse data = dashboardService.getFarmerDashboard(user.getId());
        return ResponseEntity.ok(ApiResponse.success(
                data,
                "Farmer dashboard loaded",
                "کسان ڈیش بورڈ لوڈ ہو گیا"));
    }
}