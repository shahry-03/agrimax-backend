package com.agrimax.marketplace.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "bids", schema = "marketplace")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Bid {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private SellPost post;

    @Column(name = "buyer_id", nullable = false)
    private UUID buyerId;

    @Column(name = "bid_price", nullable = false)
    private BigDecimal bidPrice;

    private String message;

    @Column(length = 20) @Builder.Default
    private String status = "PENDING";

    @CreationTimestamp @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
