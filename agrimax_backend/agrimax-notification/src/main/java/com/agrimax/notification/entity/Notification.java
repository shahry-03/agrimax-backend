package com.agrimax.notification.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "notifications", schema = "notification")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Notification {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 20)
    private String type; // PUSH, SMS, IN_APP

    @Column(name = "title_en", length = 200) private String titleEn;
    @Column(name = "title_ur", length = 200) private String titleUr;
    @Column(name = "body_en")  private String bodyEn;
    @Column(name = "body_ur")  private String bodyUr;

    @Column(name = "is_read") @Builder.Default
    private Boolean isRead = false;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @CreationTimestamp @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
