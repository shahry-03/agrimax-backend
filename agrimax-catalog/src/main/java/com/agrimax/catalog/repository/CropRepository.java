package com.agrimax.catalog.repository;

import com.agrimax.catalog.entity.Crop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CropRepository extends JpaRepository<Crop, UUID> {
    Optional<Crop> findByNameEnIgnoreCase(String nameEn);
    Optional<Crop> findByNameUrIgnoreCase(String nameUr);
    List<Crop> findByIsActiveTrue();
}
