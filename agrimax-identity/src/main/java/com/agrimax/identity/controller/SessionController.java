package com.agrimax.identity.controller;

import com.agrimax.common.dto.ApiResponse;
import com.agrimax.identity.dto.SessionResponse;
import com.agrimax.identity.entity.User;
import com.agrimax.identity.service.JwtService;
import com.agrimax.identity.service.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users/me/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final SessionService sessionService;
    private final JwtService jwtService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<SessionResponse>>> listSessions(
            @AuthenticationPrincipal User user,
            HttpServletRequest request) {
        String currentJti = extractCurrentJti(request);
        return ResponseEntity.ok(ApiResponse.success(
                sessionService.listSessions(user.getId(), currentJti),
                "Sessions loaded",
                "سیشنز لوڈ ہو گئے"));
    }

    @DeleteMapping("/{sessionId}")
    public ResponseEntity<ApiResponse<Void>> revokeSession(
            @AuthenticationPrincipal User user,
            @PathVariable UUID sessionId,
            HttpServletRequest request) {
        String currentJti = extractCurrentJti(request);
        sessionService.revokeSession(user.getId(), sessionId, currentJti);
        return ResponseEntity.ok(ApiResponse.success(
                "Session revoked",
                "سیشن ختم کر دیا گیا"));
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> revokeAllExceptCurrent(
            @AuthenticationPrincipal User user,
            HttpServletRequest request) {
        String currentJti = extractCurrentJti(request);
        int count = sessionService.revokeAllExceptCurrent(user.getId(), currentJti);
        return ResponseEntity.ok(ApiResponse.success(
                count + " other session(s) revoked",
                count + " دوسرے سیشنز ختم کر دیے گئے"));
    }

    private String extractCurrentJti(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) return null;
        try {
            return jwtService.getRefreshJtiFromToken(header.substring(7));
        } catch (Exception e) {
            return null;
        }
    }
}