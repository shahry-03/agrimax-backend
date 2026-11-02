
package com.agrimax.marketplace.handler;

import com.agrimax.catalog.entity.Crop;
import com.agrimax.catalog.service.CropService;
import com.agrimax.common.exception.ResourceNotFoundException;
import com.agrimax.common.voice.CommandHandler;
import com.agrimax.common.voice.CommandResult;
import com.agrimax.common.voice.UIAction;
import com.agrimax.marketplace.entity.SellPost;
import com.agrimax.marketplace.service.SellPostService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Handles CREATE_SELL_POST voice intent.
 *
 * Example:
 * "Mujhe 50 kilo gehu 3000 mein bechna hai"
 */
@Slf4j
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

        // 1. Authentication check
        if (userId == null) {
            return CommandResult.failWithAction(
                    "Please login first to sell your crop.",
                    "فصل بیچنے کے لیے پہلے لاگ ان کریں۔",
                    UIAction.navigate("/login", Map.of("step", "phone"))
            );
        }

        // 2. Extract crop name
        String cropName = str(parameters.get("crop"));

        if (cropName == null || cropName.isBlank()) {
            return CommandResult.failWithAction(
                    "Which crop do you want to sell?",
                    "کون سی فصل بیچنا چاہتے ہیں؟",
                    UIAction.navigate("/marketplace/sell")
            );
        }

        // 3. Extract quantity
        BigDecimal quantity = toBigDecimal(parameters.get("quantity"));

        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            return CommandResult.failWithAction(
                    "How much " + cropName + " do you want to sell? Please mention the quantity.",
                    cropName + " کتنا بیچنا چاہتے ہیں؟ براہ کرم مقدار بتائیں۔",
                    UIAction.navigate("/marketplace/sell")
            );
        }

        // 4. Extract price
        BigDecimal price = toBigDecimal(parameters.get("price"));

        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            return CommandResult.failWithAction(
                    "What price do you want for your " + cropName + "? Please mention the price.",
                    cropName + " کی کیا قیمت چاہیے؟ براہ کرم قیمت بتائیں۔",
                    UIAction.navigate("/marketplace/sell")
            );
        }

        // 5. Extract unit (defaults to kg)
        String unit = str(parameters.getOrDefault("unit", "kg"));

        if (unit == null || unit.isBlank()) {
            unit = "kg";
        }

        // 6. Find crop in catalog
        Crop crop;

        try {
            crop = cropService.findCropByName(cropName);

        } catch (ResourceNotFoundException e) {

            log.warn("Crop not found for sell post: {}", cropName);

            return CommandResult.failWithAction(
                    "Sorry, I don't have data for " + cropName + ". Try wheat, rice, cotton, or corn.",
                    "معذرت، " + cropName + " کا ڈیٹا نہیں ہے۔ گندم، چاول، کپاس، یا مکئی بولیں۔",
                    UIAction.navigate("/marketplace/sell")
            );
        }

        // 7. Create sell post
        try {
            SellPost post = sellPostService.createPost(
                    userId,
                    crop.getId(),
                    quantity,
                    unit,
                    price
            );

            log.info(
                    "Sell post created: {} | crop: {} | qty: {} {} | price: {}",
                    post.getId(),
                    crop.getNameEn(),
                    quantity,
                    unit,
                    price
            );

            String formattedQty = formatAmount(quantity);
            String formattedPrice = formatAmount(price);

            String enMsg = String.format(
                    "Your %s %s %s has been listed at PKR %s. Buyers can now see your post.",
                    formattedQty,
                    unit,
                    crop.getNameEn(),
                    formattedPrice
            );

            String urMsg = String.format(
                    "آپ کی %s %s %s کی پوسٹ %s روپے میں لگا دی گئی ہے۔ خریدار اب آپ کی پوسٹ دیکھ سکتے ہیں۔",
                    formattedQty,
                    unit,
                    crop.getNameUr(),
                    formattedPrice
            );

            // 8. Prepare response data
            Map<String, Object> data = new HashMap<>();

            data.put("postId", post.getId());
            data.put("crop", crop.getNameEn());
            data.put("cropUr", crop.getNameUr());
            data.put("quantity", quantity);
            data.put("unit", unit);
            data.put("askingPrice", price);
            data.put("status", post.getStatus());
            data.put(
                    "expiresAt",
                    post.getExpiresAt() != null
                            ? post.getExpiresAt().toString()
                            : null
            );

            // 9. Success — navigate to My Posts and highlight created post
            return CommandResult.okWithAction(
                    enMsg,
                    urMsg,
                    UIAction.navigate(
                            "/my-posts",
                            Map.of("highlightPostId", post.getId().toString())
                    ),
                    data
            );

        } catch (Exception e) {

            log.error(
                    "Failed to create sell post for user {}: {}",
                    userId,
                    e.getMessage(),
                    e
            );

            // 10. Exception handling
            return CommandResult.failWithAction(
                    "Could not create your post. Please try again.",
                    "پوسٹ نہیں بن سکی۔ دوبارہ کوشش کریں۔",
                    UIAction.navigate("/marketplace/sell")
            );
        }
    }

    // ── Helper methods ──

    private String str(Object value) {
        return value == null ? null : value.toString();
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return null;
        }

        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Format numbers nicely:
     * 50 → "50"
     * 50.5 → "50.5"
     */
    private String formatAmount(BigDecimal amount) {
        if (amount == null) {
            return "0";
        }

        NumberFormat nf = NumberFormat.getNumberInstance(Locale.US);
        nf.setMaximumFractionDigits(2);
        nf.setMinimumFractionDigits(0);

        return nf.format(amount);
    }
}
