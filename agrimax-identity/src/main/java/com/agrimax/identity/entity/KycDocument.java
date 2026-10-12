package com.agrimax.identity.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "kyc_documents", schema = "identity")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class KycDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "doc_type", nullable = false, length = 30)
    private String docType;

    @Column(name = "doc_url", nullable = false)
    private String docUrl;

    @Column(length = 20)
    @Builder.Default
    private String status = "PENDING";

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
