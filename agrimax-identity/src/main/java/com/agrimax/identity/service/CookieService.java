package com.agrimax.identity.service;

import com.agrimax.identity.config.IdentityProperties;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Getter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

@Service
@Getter
public class CookieService {

    private final String refreshTokenCookieName;
    private final boolean cookieSecure;
    private final boolean cookieHttpOnly;
    private final String cookieDomain;
    private final String cookieSameSite;

    public CookieService(IdentityProperties properties) {
        IdentityProperties.Cookie c = properties.getCookie();
        this.refreshTokenCookieName = c.getRefreshTokenName();
        this.cookieSecure = c.isSecure();
        this.cookieHttpOnly = c.isHttpOnly();
        this.cookieDomain = c.getDomain();
        this.cookieSameSite = c.getSameSite();
    }

    public void attachRefreshCookie(HttpServletResponse response, String value, int maxAgeSeconds) {
        ResponseCookie.ResponseCookieBuilder b = ResponseCookie.from(refreshTokenCookieName, value)
                .httpOnly(cookieHttpOnly)
                .secure(cookieSecure)
                .path("/")
                .maxAge(maxAgeSeconds)
                .sameSite(cookieSameSite);
        if (cookieDomain != null && !cookieDomain.isEmpty()) {
            b.domain(cookieDomain);
        }
        response.addHeader(HttpHeaders.SET_COOKIE, b.build().toString());
    }

    public void clearRefreshCookie(HttpServletResponse response) {
        ResponseCookie.ResponseCookieBuilder b = ResponseCookie.from(refreshTokenCookieName, "")
                .maxAge(0)
                .httpOnly(cookieHttpOnly)
                .path("/")
                .sameSite(cookieSameSite)
                .secure(cookieSecure);
        if (cookieDomain != null && !cookieDomain.isEmpty()) {
            b.domain(cookieDomain);
        }
        response.addHeader(HttpHeaders.SET_COOKIE, b.build().toString());
    }

    public void addNoStoreHeaders(HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setHeader("Pragma", "no-cache");
    }
}