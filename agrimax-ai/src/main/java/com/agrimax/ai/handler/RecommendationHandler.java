package com.agrimax.ai.handler;

import com.agrimax.catalog.entity.Crop;
import com.agrimax.catalog.entity.CropPrice;
import com.agrimax.catalog.service.CropService;
import com.agrimax.common.voice.CommandHandler;
import com.agrimax.common.voice.CommandResult;
import com.agrimax.ai.service.GeminiChatService;
import com.agrimax.marketplace.service.SellPostService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Handles GET_RECOMMENDATION intent — provides AI-powered farming advice.
 *
 * <p>Examples:</p>
 * <ul>
 *   <li>"Meri gehu ki fasal kab bechni chahiye?" → selling advice</li>
 *   <li>"Mausam kaisa rahega?" → weather-based planting advice</li>
 *   <li>"Meri fasal ko kya bimari hai?" → crop health advice</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecommendationHandler implements CommandHandler {

    private final CropService cropService;
    private final SellPostService sellPostService;
    private final GeminiChatService geminiChatService;

    @Override
    public String supportedIntent() {
        return "GET_RECOMMENDATION";
    }

    @Override
    public CommandResult handle(Map<String, Object> parameters, UUID userId) {
        String cropName = str(parameters.get("crop"));
        String topic = str(parameters.getOrDefault("topic", "general"));

        // Build user context (personalized advice)
        Map<String, Object> userContext = buildUserContext(userId, cropName);

        // Build enriched prompt for Gemini
        String userQuery = buildUserQuery(cropName, topic, userContext);

        log.info("Recommendation request | crop={} | topic={} | user={} | contextKeys={}",
                cropName, topic, userId, userContext.keySet());

        try {
            // Call Gemini with farming-advice-specific prompt
            String[] response = geminiChatService.getFarmingAdvice(
                    userQuery, cropName, userContext
            );

            Map<String, Object> data = new HashMap<>();
            data.put("crop", cropName);
            data.put("topic", topic);
            data.put("contextUsed", userContext.keySet());
            data.put("adviceEn", response[1]);
            data.put("adviceUr", response[0]);

            return CommandResult.ok(response[1], response[0], data);

        } catch (Exception e) {
            log.error("Farming advice failed: {}", e.getMessage(), e);
            return CommandResult.fail(
                    "Could not generate advice right now. Please try again.",
                    "ابھی مشورہ نہیں دے سکتا۔ دوبارہ کوشش کریں۔"
            );
        }
    }

    /**
     * Build context from user's data (crops, location, existing posts, prices).
     * This makes AI advice PERSONALIZED instead of generic.
     */
    private Map<String, Object> buildUserContext(UUID userId, String cropName) {
        Map<String, Object> context = new HashMap<>();

        // Add current date (for seasonal advice)
        context.put("currentDate", java.time.LocalDate.now().toString());
        context.put("season", detectSeason());

        // Add latest crop price if available
        if (cropName != null && !cropName.isBlank()) {
            try {
                Crop crop = cropService.findCropByName(cropName);
                context.put("cropNameEn", crop.getNameEn());
                context.put("cropNameUr", crop.getNameUr());
                context.put("cropCategory", crop.getCategory());
                context.put("cropUnit", crop.getUnit());

                Optional<CropPrice> latestPrice = cropService.getLatestPrice(crop.getId());
                latestPrice.ifPresent(price -> {
                    context.put("currentMinPrice", price.getMinPrice());
                    context.put("currentMaxPrice", price.getMaxPrice());
                    context.put("currentAvgPrice", price.getAvgPrice());
                    context.put("marketName", price.getMarketName());
                    context.put("priceDate", price.getPriceDate().toString());
                });
            } catch (Exception e) {
                log.debug("Could not load crop context: {}", e.getMessage());
            }
        }

        // Add user's existing posts (if any)
        if (userId != null) {
            try {
                List<?> userPosts = sellPostService.getPostsBySeller(userId);
                context.put("activePostsCount", userPosts.size());
                if (!userPosts.isEmpty()) {
                    context.put("hasActiveListings", true);
                }
            } catch (Exception e) {
                log.debug("Could not load user posts: {}", e.getMessage());
            }
        }

        return context;
    }

    /** Simple season detection for Pakistan climate. */
    private String detectSeason() {
        int month = java.time.LocalDate.now().getMonthValue();
        if (month >= 3 && month <= 5) return "Spring (Rabi harvest)";
        if (month >= 6 && month <= 9) return "Summer/Monsoon (Kharif)";
        if (month >= 10 && month <= 11) return "Autumn (Rabi planting)";
        return "Winter (Rabi growth)";
    }

    private String buildUserQuery(String cropName, String topic, Map<String, Object> context) {
        StringBuilder sb = new StringBuilder();
        if (cropName != null) {
            sb.append("Farmer is asking about ").append(cropName).append(". ");
        }
        sb.append("Topic: ").append(topic).append(". ");
        return sb.toString();
    }

    private String str(Object o) {
        return o == null ? null : o.toString();
    }
}