package com.agrimax.identity.security;

import com.agrimax.identity.service.CookieService;
import com.agrimax.identity.service.JwtService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Optional;

/**
 * Extracts refresh token from HTTP request.
 * Priority: cookie → body → X-Refresh-Token header → Authorization header.
 */
@Component
@RequiredArgsConstructor
public class RefreshTokenExtractor {

    private final CookieService cookieService;
    private final JwtService jwtService;

    public Optional<String> extract(String bodyToken, HttpServletRequest request) {
        return extractFromCookie(request)
                .or(() -> fromBody(bodyToken))
                .or(() -> fromCustomHeader(request))
                .or(() -> fromAuthorizationHeader(request));
    }

    private Optional<String> extractFromCookie(HttpServletRequest request) {
        if (request == null || request.getCookies() == null) return Optional.empty();
        return Arrays.stream(request.getCookies())
                .filter(c -> cookieService.getRefreshTokenCookieName().equals(c.getName()))
                .map(Cookie::getValue)
                .filter(v -> v != null && !v.isBlank())
                .findFirst();
    }

    private Optional<String> fromBody(String token) {
        if (token == null || token.isBlank()) return Optional.empty();
        return Optional.of(token);
    }

    private Optional<String> fromCustomHeader(HttpServletRequest request) {
        if (request == null) return Optional.empty();
        String header = request.getHeader("X-Refresh-Token");
        if (header != null && !header.isBlank()) return Optional.of(header.trim());
        return Optional.empty();
    }

    private Optional<String> fromAuthorizationHeader(HttpServletRequest request) {
        if (request == null) return Optional.empty();
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return Optional.empty();
        }
        String candidate = authHeader.substring(7).trim();
        if (candidate.isEmpty()) return Optional.empty();
        try {
            if (jwtService.isRefreshToken(candidate)) return Optional.of(candidate);
        } catch (Exception ignored) { /* not a refresh token */ }
        return Optional.empty();
    }
}