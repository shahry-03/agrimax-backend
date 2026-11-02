package com.agrimax.notification.service;

import com.agrimax.notification.entity.Notification;
import com.agrimax.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository repository;

    @Transactional
    public Notification send(UUID userId, String type, String titleEn, String titleUr,
                             String bodyEn, String bodyUr, Map<String, Object> metadata) {
        Notification notification = Notification.builder()
                .userId(userId).type(type)
                .titleEn(titleEn).titleUr(titleUr)
                .bodyEn(bodyEn).bodyUr(bodyUr)
                .metadata(metadata)
                .build();

        log.info("📩 Notification → user={}, title={}", userId, titleEn);
        return repository.save(notification);
    }

    @Transactional(readOnly = true)
    public List<Notification> getUnread(UUID userId) {
        return repository.findByUserIdAndIsReadFalseOrderByCreatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public List<Notification> getAll(UUID userId) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    // 🆕
    @Transactional(readOnly = true)
    public long getUnreadCount(UUID userId) {
        return repository.countByUserIdAndIsReadFalse(userId);
    }

    // 🆕 — single mark-as-read (scoped to user)
    @Transactional
    public boolean markAsRead(UUID notificationId, UUID userId) {
        return repository.findByIdAndUserId(notificationId, userId)
                .map(n -> {
                    if (Boolean.TRUE.equals(n.getIsRead())) return true;
                    n.setIsRead(true);
                    repository.save(n);
                    return true;
                })
                .orElse(false);
    }

    // 🆕 — bulk mark-all
    @Transactional
    public int markAllAsRead(UUID userId) {
        List<Notification> unread = repository
                .findByUserIdAndIsReadFalseOrderByCreatedAtDesc(userId);
        if (unread.isEmpty()) return 0;
        unread.forEach(n -> n.setIsRead(true));
        repository.saveAll(unread);
        return unread.size();
    }
}