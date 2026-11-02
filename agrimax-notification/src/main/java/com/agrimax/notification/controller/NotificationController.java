package com.agrimax.notification.controller;

import com.agrimax.common.dto.ApiResponse;
import com.agrimax.notification.entity.Notification;
import com.agrimax.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<Notification>>> getAll(Principal principal) {
        UUID userId = UUID.fromString(principal.getName());
        return ResponseEntity.ok(ApiResponse.success(
                notificationService.getAll(userId),
                "Notifications loaded", "اطلاعات لوڈ ہوگئیں"));
    }

    @GetMapping("/unread")
    public ResponseEntity<ApiResponse<List<Notification>>> getUnread(Principal principal) {
        UUID userId = UUID.fromString(principal.getName());
        return ResponseEntity.ok(ApiResponse.success(
                notificationService.getUnread(userId),
                "Unread notifications loaded", "غیر پڑھی اطلاعات لوڈ ہوگئیں"));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getUnreadCount(Principal principal) {
        UUID userId = UUID.fromString(principal.getName());
        long count = notificationService.getUnreadCount(userId);
        return ResponseEntity.ok(ApiResponse.success(
                Map.of("count", count), "Unread count", "غیر پڑھی تعداد"));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<ApiResponse<Void>> markAsRead(
            @PathVariable UUID id, Principal principal) {
        UUID userId = UUID.fromString(principal.getName());
        boolean ok = notificationService.markAsRead(id, userId);
        if (!ok) {
            return ResponseEntity.ok(ApiResponse.error(
                    "NOT_FOUND", "Notification not found", "اطلاع نہیں ملی"));
        }
        return ResponseEntity.ok(ApiResponse.success(
                "Marked as read", "پڑھی گئی قرار دی گئی"));
    }

    @PostMapping("/read-all")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> markAllAsRead(Principal principal) {
        UUID userId = UUID.fromString(principal.getName());
        int count = notificationService.markAllAsRead(userId);
        return ResponseEntity.ok(ApiResponse.success(
                Map.of("count", count),
                "All marked as read", "سب پڑھی گئی قرار دی گئیں"));
    }
}