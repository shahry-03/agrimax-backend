package com.agrimax.notification.handler;

import com.agrimax.common.voice.CommandHandler;
import com.agrimax.common.voice.CommandResult;
import com.agrimax.common.voice.UIAction;
import com.agrimax.notification.entity.Notification;
import com.agrimax.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class ViewNotificationsHandler implements CommandHandler {

    private final NotificationService notificationService;

    @Override
    public String supportedIntent() {
        return "VIEW_NOTIFICATIONS";
    }

    @Override
    public CommandResult handle(Map<String, Object> parameters, UUID userId) {
        if (userId == null) {
            return CommandResult.failWithAction(
                    "Please login to view your notifications.",
                    "اطلاعات دیکھنے کے لیے پہلے لاگ ان کریں۔",
                    UIAction.navigate("/login", Map.of("step", "phone")));
        }

        try {
            long unread = notificationService.getUnreadCount(userId);
            List<Notification> all = notificationService.getAll(userId);

            String enMsg;
            String urMsg;
            if (all.isEmpty()) {
                enMsg = "You have no notifications yet.";
                urMsg = "ابھی تک کوئی اطلاع نہیں ہے۔";
            } else if (unread == 0) {
                enMsg = "You have " + all.size() + " notifications, all read.";
                urMsg = "آپ کے پاس " + all.size() + " اطلاعات ہیں، سب پڑھی گئیں۔";
            } else {
                enMsg = "You have " + unread + " unread notification" + (unread == 1 ? "" : "s") +
                        " out of " + all.size() + " total.";
                urMsg = "آپ کے پاس " + all.size() + " میں سے " + unread +
                        " غیر پڑھی اطلاعات ہیں۔";
            }

            // Return top 10 for the payload (avoid huge JSON)
            List<Map<String, Object>> preview = all.stream()
                    .limit(10)
                    .map(n -> Map.<String, Object>of(
                            "id", n.getId().toString(),
                            "type", n.getType(),
                            "titleEn", n.getTitleEn() != null ? n.getTitleEn() : "",
                            "titleUr", n.getTitleUr() != null ? n.getTitleUr() : "",
                            "bodyEn", n.getBodyEn() != null ? n.getBodyEn() : "",
                            "bodyUr", n.getBodyUr() != null ? n.getBodyUr() : "",
                            "isRead", Boolean.TRUE.equals(n.getIsRead()),
                            "createdAt", n.getCreatedAt().toString()
                    ))
                    .toList();

            Map<String, Object> data = Map.of(
                    "total", all.size(),
                    "unread", unread,
                    "preview", preview
            );

            return CommandResult.okWithAction(enMsg, urMsg,
                    UIAction.navigate("/notifications"),
                    data);

        } catch (Exception e) {
            log.error("Failed to load notifications for {}: {}", userId, e.getMessage(), e);
            return CommandResult.failWithAction(
                    "Could not load notifications. Please try again.",
                    "اطلاعات لوڈ نہیں ہو سکیں۔ دوبارہ کوشش کریں۔",
                    UIAction.navigate("/notifications"));
        }
    }
}