package com.agrimax.identity.controller;

import com.agrimax.common.dto.ApiResponse;
import com.agrimax.common.ratelimit.RateLimit;
import com.agrimax.identity.dto.*;
import com.agrimax.identity.service.BuyerAuthService;
import com.agrimax.identity.service.CookieService;
import com.agrimax.identity.service.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.agrimax.identity.service.TwoFactorAuthService;

@RestController
@RequestMapping("/api/v1/auth/buyer")
@RequiredArgsConstructor
public class BuyerAuthController {

    private final BuyerAuthService buyerAuthService;
    private final CookieService cookieService;
    private final JwtService jwtService;
    private final TwoFactorAuthService twoFactorAuthService;

    @PostMapping("/register")
    @RateLimit(name = "buyer-register", capacity = 3, refillTokens = 3, refillPeriod = 1,
               refillUnit = RateLimit.RefillUnit.HOURS)
    public ResponseEntity<ApiResponse<UserResponse>> register(
            @Valid @RequestBody BuyerRegisterRequest request) {
        UserResponse user = buyerAuthService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(user,
                        "Registration successful. Please verify your email.",
                        "رجسٹریشن کامیاب۔ براہ کرم ای میل تصدیق کریں۔"));
    }

    @PostMapping("/login")
    @RateLimit(name = "buyer-login", capacity = 5, refillTokens = 5, refillPeriod = 15)
    public ResponseEntity<ApiResponse<TokenResponse>> login(
            @Valid @RequestBody BuyerLoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse response) {

        String ip = extractIp(httpRequest);
        String ua = httpRequest.getHeader("User-Agent");

        TokenResponse tokens = buyerAuthService.login(request, ip, ua);

        // 2FA required → no cookie yet
        if (tokens.requiresTwoFactor()) {
            return ResponseEntity.ok(ApiResponse.success(tokens,
                    "Two-factor authentication required",
                    "دو مرحلہ تصدیق ضروری ہے"));
        }

        attachRefreshCookie(response, tokens.refreshToken());
        cookieService.addNoStoreHeaders(response);

        return ResponseEntity.ok(ApiResponse.success(tokens,
                "Login successful", "لاگ ان کامیاب"));
    }

    @PostMapping("/verify-email")
    public ResponseEntity<ApiResponse<Void>> verifyEmail(
            @Valid @RequestBody VerifyEmailRequest request) {
        buyerAuthService.verifyEmail(request.token());
        return ResponseEntity.ok(ApiResponse.success(
                "Email verified. You can now log in.",
                "ای میل تصدیق ہو گئی۔ اب لاگ ان کر سکتے ہیں۔"));
    }

    @PostMapping("/resend-verification")
    @RateLimit(name = "buyer-resend-verif", capacity = 3, refillTokens = 3,
               refillPeriod = 1, refillUnit = RateLimit.RefillUnit.HOURS)
    public ResponseEntity<ApiResponse<Void>> resendVerification(
            @Valid @RequestBody ResendVerificationRequest request) {
        buyerAuthService.resendVerificationEmail(request.email());
        return ResponseEntity.ok(ApiResponse.success(
                "If the email is registered and unverified, a verification link has been sent",
                "اگر ای میل رجسٹرڈ ہے تو تصدیقی لنک بھیج دیا گیا ہے"));
    }

    @PostMapping("/forgot-password")
    @RateLimit(name = "buyer-forgot-pwd", capacity = 5, refillTokens = 5,
               refillPeriod = 1, refillUnit = RateLimit.RefillUnit.HOURS)
    public ResponseEntity<ApiResponse<Void>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {
        buyerAuthService.forgotPassword(request.email());
        return ResponseEntity.ok(ApiResponse.success(
                "If the email is registered, a password reset link has been sent",
                "اگر ای میل رجسٹرڈ ہے تو پاس ورڈ ری سیٹ لنک بھیج دیا گیا ہے"));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {
        buyerAuthService.resetPassword(request);
        return ResponseEntity.ok(ApiResponse.success(
                "Password reset successful. Please log in.",
                "پاس ورڈ ری سیٹ کامیاب۔ براہ کرم لاگ ان کریں۔"));
    }

    // Add method (before the "// ── helpers ──" section):
    @PostMapping("/2fa/verify")
    @RateLimit(name = "buyer-2fa-verify", capacity = 10, refillTokens = 10, refillPeriod = 15)
    public ResponseEntity<ApiResponse<TokenResponse>> verify2fa(
            @Valid @RequestBody TwoFactorVerifyRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse response) {

        String ip = extractIp(httpRequest);
        String ua = httpRequest.getHeader("User-Agent");

        TokenResponse tokens = twoFactorAuthService.verifyAndCompleteLogin(request, ip, ua);

        attachRefreshCookie(response, tokens.refreshToken());
        cookieService.addNoStoreHeaders(response);

        return ResponseEntity.ok(ApiResponse.success(tokens,
                "2FA login successful", "دو مرحلہ تصدیق کامیاب"));
    }

    // ── helpers ──

    private void attachRefreshCookie(HttpServletResponse response, String refreshToken) {
        int maxAgeSeconds = (int) (jwtService.getRefreshExpiryMs() / 1000);
        cookieService.attachRefreshCookie(response, refreshToken, maxAgeSeconds);
    }

    private String extractIp(HttpServletRequest request) {
        String fwd = request.getHeader("X-Forwarded-For");
        if (fwd != null && !fwd.isBlank()) return fwd.split(",")[0].trim();
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) return realIp.trim();
        return request.getRemoteAddr();
    }
}