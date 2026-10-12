package com.agrimax.catalog.handler;

import com.agrimax.catalog.entity.Crop;
import com.agrimax.catalog.entity.CropPrice;
import com.agrimax.catalog.service.CropService;
import com.agrimax.common.voice.CommandHandler;
import com.agrimax.common.voice.CommandResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Handles GET_CROP_PRICE voice intent.
 * Example: "Aaj gehu ki price kya hai?" → returns latest wheat price.
 */
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
            return CommandResult.fail("Please tell me which crop", "فصل کا نام بتائیں");
        }

        try {
            Crop crop = cropService.findCropByName(cropName);
            return cropService.getLatestPrice(crop.getId())
                    .map(price -> CommandResult.ok(
                            String.format("Today's %s price is PKR %s to %s per %s",
                                    crop.getNameEn(), price.getMinPrice(), price.getMaxPrice(), crop.getUnit()),
                            String.format("آج %s کی قیمت %s سے %s روپے فی %s ہے",
                                    crop.getNameUr(), price.getMinPrice(), price.getMaxPrice(), crop.getUnit()),
                            Map.of("crop", crop.getNameEn(), "min", price.getMinPrice(),
                                    "max", price.getMaxPrice(), "avg", price.getAvgPrice())
                    ))
                    .orElse(CommandResult.fail(
                            "No price data available for " + crop.getNameEn(),
                            crop.getNameUr() + " کی قیمت دستیاب نہیں ہے"
                    ));
        } catch (Exception e) {
            return CommandResult.fail("Crop not found: " + cropName, "فصل نہیں ملی: " + cropName);
        }
    }
}
