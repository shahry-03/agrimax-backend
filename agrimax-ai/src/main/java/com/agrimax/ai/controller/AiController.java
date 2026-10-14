package com.agrimax.ai.controller;

import com.agrimax.ai.cache.GeminiResponseCache;
import com.agrimax.ai.service.GeminiChatService;
import com.agrimax.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.agrimax.ai.llm.HybridLlmService;
import com.agrimax.ai.service.LanguageDetector;

import java.time.LocalDate;
import java.util.Map;

/**
 * Public AI endpoints used by the frontend (no auth required):
 * - GET /api/v1/ai/tip — daily agricultural tip (Gemini-powered, cached daily)
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class AiController {

    private final GeminiChatService geminiChatService;
    private final GeminiResponseCache geminiCache;
    private final HybridLlmService hybridLlmService;
    private final LanguageDetector languageDetector;

    // Simple daily tip cache — regenerated once per day
    private volatile Map<String, String> cachedTip = null;
    private volatile LocalDate cacheDate = null;

    // Static fallback in case everything fails
    private static final Map<String, String> FALLBACK_TIP = Map.of(
            "titleUr", "آج کی زرعی ٹپ",
            "titleEn", "Today's Farming Tip",
            "tipUr", "اس موسم میں اپنی فصل کی آبپاشی باقاعدگی سے کریں۔ صبح یا شام کو پانی دینا بہتر ہے۔",
            "tipEn", "Irrigate your crops regularly this season. Morning or evening watering is best for optimal growth.",
            "category", "irrigation"
    );

    /**
     * GET /api/v1/ai/tip
     * Returns today's AI-generated agricultural tip in Urdu + English.
     * Cached per day — only calls Gemini once every 24 hours.
     * Always returns 200 with either a Gemini tip or a static fallback.
     */
    @GetMapping("/tip")
    public ResponseEntity<ApiResponse<Map<String, String>>> getDailyTip() {
        try {
            LocalDate today = LocalDate.now();

            // Return cached tip if it's from today
            if (cachedTip != null && today.equals(cacheDate)) {
                log.debug("Returning cached daily tip for {}", today);
                return ResponseEntity.ok(ApiResponse.success(
                        cachedTip, "Daily tip loaded", "آج کی ٹپ لوڈ ہوگئی"));
            }

            // Generate new tip via Gemini (with internal fallback)
            log.info("Generating new daily tip from Gemini for {}", today);
            Map<String, String> tip = geminiChatService.getDailyTip();
            if (tip != null && !tip.isEmpty()) {
                cachedTip = tip;
                cacheDate = today;
            } else {
                cachedTip = FALLBACK_TIP;
                cacheDate = today;
            }

            return ResponseEntity.ok(ApiResponse.success(
                    cachedTip, "Daily tip loaded", "آج کی ٹپ لوڈ ہوگئی"));

        } catch (Exception e) {
            // Absolute last resort — should never happen but guarantees no 500
            log.error("Failed to generate daily tip: {}", e.getMessage(), e);
            return ResponseEntity.ok(ApiResponse.success(
                    FALLBACK_TIP, "Daily tip loaded (fallback)", "آج کی ٹپ لوڈ ہوگئی"));
        }
    }

    /**
     * GET /api/v1/ai/cache/stats — for monitoring
     */
    @GetMapping("/cache/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCacheStats() {
        Map<String, Object> stats = Map.of(
                "intentCacheSize", geminiCache.size(),
                "tipCached", cachedTip != null,
                "tipCacheDate", cacheDate != null ? cacheDate.toString() : "none"
        );
        return ResponseEntity.ok(ApiResponse.success(stats, "Cache stats", "کیش معلومات"));
    }


    @GetMapping("/llm/status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getLlmStatus() {
        return ResponseEntity.ok(ApiResponse.success(
                hybridLlmService.getStatus(),
                "LLM status loaded",
                "AI سروسز کی معلومات"
        ));
    }

    @GetMapping("/language/metrics")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getLanguageMetrics() {
        return ResponseEntity.ok(ApiResponse.success(
                languageDetector.getMetrics(),
                "Language metrics loaded",
                "زبان کی معلومات"
        ));
    }
}
