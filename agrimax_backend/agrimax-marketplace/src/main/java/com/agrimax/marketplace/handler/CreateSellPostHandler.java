package com.agrimax.marketplace.handler;

import com.agrimax.catalog.entity.Crop;
import com.agrimax.catalog.service.CropService;
import com.agrimax.common.voice.CommandHandler;
import com.agrimax.common.voice.CommandResult;
import com.agrimax.marketplace.entity.SellPost;
import com.agrimax.marketplace.service.SellPostService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Handles CREATE_SELL_POST voice intent.
 * Example: "Mujhe 50 kilo gehu 3000 mein bechna hai"
 */
@Component
@RequiredArgsConstructor
public class CreateSellPostHandler implements CommandHandler {

    private final SellPostService sellPostService;
    private final CropService cropService;

    @Override
    public String supportedIntent() {
        return "CREATE_SELL_POST";
    }

    @Override
    public CommandResult handle(Map<String, Object> parameters, UUID userId) {
        if (userId == null) {
            return CommandResult.fail("Please login first", "پہلے لاگ ان کریں");
        }

        String cropName = (String) parameters.get("crop");
        if (cropName == null) {
            return CommandResult.fail("Which crop do you want to sell?", "کون سی فصل بیچنا چاہتے ہیں؟");
        }

        try {
            Crop crop = cropService.findCropByName(cropName);
            BigDecimal quantity = toBigDecimal(parameters.get("quantity"), BigDecimal.ONE);
            BigDecimal price = toBigDecimal(parameters.get("price"), BigDecimal.ZERO);
            String unit = parameters.getOrDefault("unit", "kg").toString();

            SellPost post = sellPostService.createPost(userId, crop.getId(), quantity, unit, price);

            return CommandResult.ok(
                    String.format("Your %s kg %s post has been listed at PKR %s",
                            quantity, crop.getNameEn(), price),
                    String.format("آپ کی %s کلو %s کی پوسٹ %s روپے میں لگا دی گئی ہے",
                            quantity, crop.getNameUr(), price),
                    Map.of("postId", post.getId())
            );
        } catch (Exception e) {
            return CommandResult.fail("Could not create post: " + e.getMessage(),
                    "پوسٹ نہیں بنائی جا سکی۔ دوبارہ کوشش کریں۔");
        }
    }

    private BigDecimal toBigDecimal(Object value, BigDecimal fallback) {
        if (value == null) return fallback;
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
