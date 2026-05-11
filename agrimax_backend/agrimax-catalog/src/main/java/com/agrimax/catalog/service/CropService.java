package com.agrimax.catalog.service;

import com.agrimax.catalog.entity.Crop;
import com.agrimax.catalog.entity.CropPrice;
import com.agrimax.catalog.repository.CropPriceRepository;
import com.agrimax.catalog.repository.CropRepository;
import com.agrimax.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CropService {

    private final CropRepository cropRepository;
    private final CropPriceRepository priceRepository;

    @Transactional(readOnly = true)
    public List<Crop> getAllActiveCrops() {
        return cropRepository.findByIsActiveTrue();
    }

    @Transactional(readOnly = true)
    public Crop findCropByName(String name) {
        return cropRepository.findByNameEnIgnoreCase(name)
                .or(() -> cropRepository.findByNameUrIgnoreCase(name))
                .orElseThrow(() -> new ResourceNotFoundException("Crop not found: " + name));
    }

    @Transactional(readOnly = true)
    public Optional<CropPrice> getLatestPrice(UUID cropId) {
        return priceRepository.findTopByCrop_IdOrderByPriceDateDesc(cropId);
    }

    @Transactional(readOnly = true)
    public Crop getCropById(UUID cropId) {
        return cropRepository.findById(cropId)
                .orElseThrow(() -> new ResourceNotFoundException("Crop not found: " + cropId));
    }
}
