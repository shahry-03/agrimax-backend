package com.agrimax.catalog.repository;

import com.agrimax.catalog.entity.CropPrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CropPriceRepository extends JpaRepository<CropPrice, UUID> {
    Optional<CropPrice> findTopByCrop_IdOrderByPriceDateDesc(UUID cropId);
    List<CropPrice> findByCrop_IdOrderByPriceDateDesc(UUID cropId);
}
