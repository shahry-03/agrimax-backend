package com.agrimax.catalog.handler;

import com.agrimax.catalog.entity.Crop;
import com.agrimax.catalog.entity.CropPrice;
import com.agrimax.catalog.service.CropService;
import com.agrimax.common.exception.ResourceNotFoundException;
import com.agrimax.common.voice.CommandHandler;
import com.agrimax.common.voice.CommandResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Handles GET_CROP_PRICE voice intent.
 * Example: "Aaj gehu ki price kya hai?" → returns latest wheat price.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GetCropPriceHandler implements CommandHandler {

    private final CropService cropService;

    @Override
    public String supportedIntent() {
        return "GET_CROP_PRICE";
    }

    @Override
    public CommandResult handle(Map<String, Object> parameters, UUID userId) {
        String cropName = (String) parameters.get("crop");
        
        if (cropName == null || cropName.isBlank()) {
            return CommandResult.fail(
                    "Which crop's price do you want to know?",
                    "کس فصل کی قیمت جاننی ہے؟"
            );
        }

        Crop crop;
        try {
            crop = cropService.findCropByName(cropName);
        } catch (ResourceNotFoundException e) {
            log.warn("Crop not found: {}", cropName);
            return CommandResult.fail(
                    "Sorry, I don't have data for " + cropName + ". Try wheat, rice, cotton, or corn.",
                    "معذرت، " + cropName + " کا ڈیٹا نہیں ہے۔ گندم، چاول، کپاس، یا مکئی پوچھیں۔"
            );
        }

        return cropService.getLatestPrice(crop.getId())
                .map(price -> {
                    // Build bilingual message
                    String enMsg = String.format(
                            "Today's %s price in %s is PKR %s to %s per %s (average: PKR %s)",
                            crop.getNameEn(),
                            price.getMarketName() != null ? price.getMarketName() : "market",
                            price.getMinPrice(),
                            price.getMaxPrice(),
                            crop.getUnit(),
                            price.getAvgPrice()
                    );
                    String urMsg = String.format(
                            "آج %s کی قیمت %s میں %s سے %s روپے فی %s ہے (اوسط: %s روپے)",
                            crop.getNameUr(),
                            price.getMarketName() != null ? price.getMarketName() : "مارکیٹ",
                            price.getMinPrice(),
                            price.getMaxPrice(),
                            crop.getUnit(),
                            price.getAvgPrice()
                    );

                    Map<String, Object> data = new HashMap<>();
                    data.put("crop", crop.getNameEn());
                    data.put("cropUr", crop.getNameUr());
                    data.put("minPrice", price.getMinPrice());
                    data.put("maxPrice", price.getMaxPrice());
                    data.put("avgPrice", price.getAvgPrice());
                    data.put("unit", crop.getUnit());
                    data.put("market", price.getMarketName());
                    data.put("date", price.getPriceDate().toString());

                    return CommandResult.ok(enMsg, urMsg, data);
                })
                .orElseGet(() -> {
                    log.warn("No price data for crop: {}", crop.getNameEn());
                    return CommandResult.fail(
                            "Price data for " + crop.getNameEn() + " is not available yet.",
                            crop.getNameUr() + " کی قیمت ابھی دستیاب نہیں ہے۔"
                    );
                });
    }
}