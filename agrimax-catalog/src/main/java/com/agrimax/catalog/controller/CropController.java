package com.agrimax.catalog.controller;

import com.agrimax.catalog.entity.Crop;
import com.agrimax.catalog.entity.CropPrice;
import com.agrimax.catalog.service.CropService;
import com.agrimax.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/crops")
@RequiredArgsConstructor
public class CropController {

    private final CropService cropService;

    @GetMapping("/public/list")
    public ResponseEntity<ApiResponse<List<Crop>>> listCrops() {
        return ResponseEntity.ok(ApiResponse.success(
                cropService.getAllActiveCrops(), "Crops loaded", "فصلیں لوڈ ہوگئیں"));
    }

    @GetMapping("/public/{id}/price")
    public ResponseEntity<ApiResponse<CropPrice>> getLatestPrice(@PathVariable UUID id) {
        CropPrice price = cropService.getLatestPrice(id)
                .orElse(null);
        return ResponseEntity.ok(ApiResponse.success(price, "Price loaded", "قیمت لوڈ ہوگئی"));
    }
}
