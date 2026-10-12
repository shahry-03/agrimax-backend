package com.agrimax.catalog.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "crops", schema = "catalog")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Crop {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "name_en", nullable = false, length = 100)
    private String nameEn;

    @Column(name = "name_ur", nullable = false, length = 100)
    private String nameUr;

    @Column(length = 50)
    private String category;

    @Column(length = 20) @Builder.Default
    private String unit = "kg";

    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "is_active") @Builder.Default
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
