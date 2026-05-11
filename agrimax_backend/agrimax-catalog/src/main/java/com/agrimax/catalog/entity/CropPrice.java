package com.agrimax.catalog.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "crop_prices", schema = "catalog")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CropPrice {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "crop_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Crop crop;

    @Column(name = "market_name", length = 100)
    private String marketName;

    @Column(name = "min_price") private BigDecimal minPrice;
    @Column(name = "max_price") private BigDecimal maxPrice;
    @Column(name = "avg_price") private BigDecimal avgPrice;

    @Column(length = 50) @Builder.Default
    private String source = "MANUAL";

    @Column(name = "price_date", nullable = false)
    private LocalDate priceDate;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
