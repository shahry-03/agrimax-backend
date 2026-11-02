package com.agrimax.dashboard.controller;

import com.agrimax.common.dto.ApiResponse;
import com.agrimax.dashboard.dto.AdminDashboardResponse;
import com.agrimax.dashboard.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<AdminDashboardResponse>> getDashboard() {
        AdminDashboardResponse data = dashboardService.getAdminDashboard();
        return ResponseEntity.ok(ApiResponse.success(
                data,
                "Admin dashboard loaded",
                "ایڈمن ڈیش بورڈ لوڈ ہو گیا"));
    }
}