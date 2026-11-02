package com.agrimax.identity.controller;

import com.agrimax.common.dto.ApiResponse;
import com.agrimax.identity.dto.*;
import com.agrimax.identity.entity.User;
import com.agrimax.identity.service.TwoFactorAuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 2FA management — authenticated endpoints.
 * Buyer/Admin can enable 2FA. Farmer uses OTP (no password → 2FA not applicable).
 */
@RestController
@RequestMapping("/api/v1/users/me/2fa")
@RequiredArgsConstructor
public class UserTwoFactorController {

    private final TwoFactorAuthService twoFactorAuthService;

    @PostMapping("/setup")
    public ResponseEntity<ApiResponse<TwoFactorSetupResponse>> setup(
            @AuthenticationPrincipal User user) {
        TwoFactorSetupResponse response = twoFactorAuthService.setup(user);
        return ResponseEntity.ok(ApiResponse.success(response,
                "Scan the QR code with your authenticator app",
                "اپنے authenticator ایپ سے QR کوڈ اسکین کریں"));
    }

    @PostMapping("/enable")
    public ResponseEntity<ApiResponse<TwoFactorEnableResponse>> enable(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody TwoFactorEnableRequest request) {
        TwoFactorEnableResponse response = twoFactorAuthService.enable(user, request);
        return ResponseEntity.ok(ApiResponse.success(response,
                "2FA enabled. Save your backup codes in a safe place.",
                "2FA فعال ہو گیا۔ بیک اپ کوڈز محفوظ جگہ رکھیں۔"));
    }

    @PostMapping("/disable")
    public ResponseEntity<ApiResponse<Void>> disable(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody TwoFactorDisableRequest request) {
        twoFactorAuthService.disable(user, request);
        return ResponseEntity.ok(ApiResponse.success(
                "2FA disabled. All sessions have been logged out.",
                "2FA غیر فعال۔ تمام سیشن لاگ آؤٹ ہو گئے۔"));
    }

    @GetMapping("/status")
    public ResponseEntity<ApiResponse<TwoFactorStatusResponse>> status(
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.success(
                twoFactorAuthService.getStatus(user),
                "2FA status",
                "2FA کی صورتحال"));
    }
}