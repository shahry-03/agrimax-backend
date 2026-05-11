package com.agrimax.marketplace.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "deals", schema = "marketplace")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Deal {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id")
    private SellPost post;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bid_id")
    private Bid bid;

    @Column(name = "seller_id", nullable = false) private UUID sellerId;
    @Column(name = "buyer_id", nullable = false)  private UUID buyerId;
    @Column(name = "final_price", nullable = false) private BigDecimal finalPrice;
    @Column(nullable = false) private BigDecimal quantity;

    @Column(length = 20) @Builder.Default
    private String status = "PENDING";

    @Column(name = "completed_at") private Instant completedAt;

    @CreationTimestamp @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
