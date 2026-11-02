package com.agrimax.notification.repository;

import com.agrimax.notification.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    List<Notification> findByUserIdOrderByCreatedAtDesc(UUID userId);

    List<Notification> findByUserIdAndIsReadFalseOrderByCreatedAtDesc(UUID userId);

    // 🆕 — for mark-as-read (scoped to user, so no cross-user leaks)
    Optional<Notification> findByIdAndUserId(UUID id, UUID userId);

    // 🆕 — unread count for badge
    long countByUserIdAndIsReadFalse(UUID userId);
}