package com.agrimax.fintech.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "escrow", schema = "fintech")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Escrow {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "deal_id", nullable = false, unique = true)
    private UUID dealId;

    @Column(name = "buyer_id", nullable = false)  private UUID buyerId;
    @Column(name = "seller_id", nullable = false) private UUID sellerId;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(length = 20) @Builder.Default
    private String status = "HELD";

    @CreationTimestamp @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "released_at")
    private Instant releasedAt;
}
