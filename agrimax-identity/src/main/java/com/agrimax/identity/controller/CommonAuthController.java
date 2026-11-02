package com.agrimax.identity.controller;

import com.agrimax.common.dto.ApiResponse;
import com.agrimax.common.ratelimit.RateLimit;
import com.agrimax.identity.dto.RefreshTokenRequest;
import com.agrimax.identity.dto.TokenResponse;
import com.agrimax.identity.entity.RefreshToken;
import com.agrimax.identity.entity.User;
import com.agrimax.identity.security.RefreshTokenExtractor;
import com.agrimax.identity.service.CookieService;
import com.agrimax.identity.service.JwtService;
import com.agrimax.identity.service.RefreshTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class CommonAuthController {

    private final RefreshTokenService refreshTokenService;
    private final RefreshTokenExtractor refreshTokenExtractor;
    private final CookieService cookieService;
    private final JwtService jwtService;

    @PostMapping("/refresh")
    @RateLimit(name = "refresh", capacity = 30, refillTokens = 30,
               refillPeriod = 1, refillUnit = RateLimit.RefillUnit.MINUTES)
    public ResponseEntity<ApiResponse<TokenResponse>> refresh(
            @RequestBody(required = false) RefreshTokenRequest body,
            HttpServletRequest request,
            HttpServletResponse response) {

        String raw = refreshTokenExtractor
                .extract(body != null ? body.refreshToken() : null, request)
                .orElseThrow(() -> new org.springframework.security.authentication
                        .BadCredentialsException("Refresh token missing"));

        RefreshToken old = refreshTokenService.validateAndGet(raw);
        RefreshToken newRt = refreshTokenService.rotate(old);
        User user = newRt.getUser();

        String accessToken = jwtService.generateAccessToken(user, newRt.getJti());
        String refreshToken = jwtService.generateRefreshToken(user, newRt.getJti());

        int maxAgeSeconds = (int) (jwtService.getRefreshExpiryMs() / 1000);
        cookieService.attachRefreshCookie(response, refreshToken, maxAgeSeconds);
        cookieService.addNoStoreHeaders(response);

        return ResponseEntity.ok(ApiResponse.success(
                TokenResponse.of(accessToken, refreshToken, jwtService.getAccessExpiryMs(), user),
                "Token refreshed", "ٹوکن ریفریش ہو گیا"));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestBody(required = false) RefreshTokenRequest body,
            HttpServletRequest request,
            HttpServletResponse response) {

        refreshTokenExtractor.extract(body != null ? body.refreshToken() : null, request)
                .ifPresent(refreshTokenService::revokeByValue);

        cookieService.clearRefreshCookie(response);
        cookieService.addNoStoreHeaders(response);
        SecurityContextHolder.clearContext();

        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}