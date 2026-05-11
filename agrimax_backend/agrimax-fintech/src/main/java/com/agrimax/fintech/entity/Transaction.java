package com.agrimax.fintech.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transactions", schema = "fintech")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Transaction {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wallet_id", nullable = false)
    private Wallet wallet;

    @Column(nullable = false, length = 20)
    private String type; // CREDIT, DEBIT, ESCROW_HOLD, ESCROW_RELEASE

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(name = "reference_id")    private UUID referenceId;
    @Column(name = "reference_type", length = 30) private String referenceType;

    private String description;

    @Column(length = 20) @Builder.Default
    private String status = "COMPLETED";

    @CreationTimestamp @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
