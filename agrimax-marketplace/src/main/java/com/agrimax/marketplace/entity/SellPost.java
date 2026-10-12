package com.agrimax.marketplace.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sell_posts", schema = "marketplace")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SellPost {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "seller_id", nullable = false)
    private UUID sellerId;

    @Column(name = "crop_id", nullable = false)
    private UUID cropId;

    @Column(nullable = false)
    private BigDecimal quantity;

    @Column(length = 20) @Builder.Default
    private String unit = "kg";

    @Column(name = "asking_price", nullable = false)
    private BigDecimal askingPrice;

    private String description;

    @Column(length = 20) @Builder.Default
    private String status = "ACTIVE";

    @Column(length = 200)
    private String location;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @CreationTimestamp @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp @Column(name = "updated_at")
    private Instant updatedAt;
}
