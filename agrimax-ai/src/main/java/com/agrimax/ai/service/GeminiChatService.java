package com.agrimax.ai.service;

import com.agrimax.ai.llm.HybridLlmService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * High-level AI chat service — delegates to HybridLlmService (Gemini + Groq).
 * Handles:
 * - Help responses for UNKNOWN intent
 * - Daily agricultural tips
 * - Personalized farming advice
 */
@Slf4j
@Service
public class GeminiChatService {

    private final HybridLlmService llm;
    private final ObjectMapper objectMapper;

    private static final String HELP_SYSTEM_PROMPT = """
            You are AgriMax AI, a helpful agricultural assistant for Pakistani farmers.
            When a farmer says something you don't understand or that's not about farming,
            respond warmly in BOTH Urdu (Roman script) and English.
            
            Format your response as JSON:
            {"urdu": "Urdu Roman response here", "english": "English response here"}
            
            Keep responses short (1-2 sentences), friendly, and farmer-appropriate.
            Suggest what the farmer can ask about: crop prices, selling crops, wallet balance.
            """;

    private static final String TIP_SYSTEM_PROMPT = """
            You are an agricultural advisor for Pakistani farmers.
            Generate a single, practical farming tip for today (%s).
            
            Consider the current season (Pakistan climate) and common crops:
            wheat, rice, corn, cotton, sugarcane, mustard, chickpea, potato, onion, tomato.
            
            Format as JSON only:
            {
              "titleUr": "Urdu Roman title (short, 5-8 words)",
              "titleEn": "English title (short, 5-8 words)",
              "tipUr": "Tip in Urdu Roman (2-3 sentences)",
              "tipEn": "Tip in English (2-3 sentences)",
              "category": "one of: price|planting|harvest|weather|pest|irrigation|market"
            }
            """.formatted(LocalDate.now());

    public GeminiChatService(HybridLlmService llm, ObjectMapper objectMapper) {
        this.llm = llm;
        this.objectMapper = objectMapper;
    }

    // ══════════════════════════════════════════════════════════
    // PUBLIC API (signatures unchanged — handlers not affected)
    // ══════════════════════════════════════════════════════════

    public String[] getHelpResponse(String userText, String sessionId) {
        log.info("Getting help response for: \"{}\"", userText);
        try {
            String content = llm.chat(HELP_SYSTEM_PROMPT, userText, 0.7, 150);
            JsonNode node = objectMapper.readTree(content);
            String urdu = node.has("urdu") ? node.get("urdu").asText() : getFallbackUrdu();
            String english = node.has("english") ? node.get("english").asText() : getFallbackEnglish();
            return new String[]{urdu, english};
        } catch (Exception e) {
            log.warn("Help response failed ({}), using template", e.getMessage());
            return new String[]{getFallbackUrdu(), getFallbackEnglish()};
        }
    }

    public Map<String, String> getDailyTip() {
        log.info("Generating daily tip via hybrid LLM");
        try {
            String content = llm.chat(TIP_SYSTEM_PROMPT, "Generate today's farming tip", 0.8, 300);
            JsonNode node = objectMapper.readTree(content);
            return Map.of(
                    "titleUr", getTextSafe(node, "titleUr", "آج کی زرعی ٹپ"),
                    "titleEn", getTextSafe(node, "titleEn", "Today's Farming Tip"),
                    "tipUr", getTextSafe(node, "tipUr", "اپنی فصل کی دیکھ بھال کریں"),
                    "tipEn", getTextSafe(node, "tipEn", "Take good care of your crops"),
                    "category", getTextSafe(node, "category", "market")
            );
        } catch (Exception e) {
            log.warn("Daily tip failed ({}), using template", e.getMessage());
            return getFallbackTip();
        }
    }

    public String[] getFarmingAdvice(String userQuery, String cropName, Map<String, Object> context) {
        log.info("Getting farming advice | crop={} | contextKeys={}", cropName, context.keySet());

        StringBuilder contextStr = new StringBuilder();
        context.forEach((k, v) -> contextStr.append("- ").append(k).append(": ").append(v).append("\n"));

        String systemPrompt = """
                You are an experienced agricultural advisor for Pakistani farmers with 20+ years
                of experience. You provide PRACTICAL, ACTIONABLE advice in simple language.

                === Farmer's Context ===
                %s

                === Response Guidelines ===
                1. Give SPECIFIC advice, not generic tips
                2. Mention current prices if available in context
                3. Consider the current season (%s)
                4. Recommend concrete actions (e.g., "wait 2 weeks", "sell now", "irrigate daily")
                5. Warn about common risks (weather, pests, price drops)
                6. Keep response SHORT (2-3 sentences, farmer-friendly)
                7. If Urdu Roman requested, use Urdu Roman script (not Devanagari)

                === Response Format (JSON only) ===
                {
                  "urdu": "Urdu Roman advice (2-3 sentences)",
                  "english": "English advice (2-3 sentences)",
                  "confidence": 0.0-1.0,
                  "actionable": true
                }
                """.formatted(contextStr.toString(), context.getOrDefault("season", "unknown"));

        try {
            String content = llm.chat(systemPrompt, userQuery, 0.7, 300);
            JsonNode node = objectMapper.readTree(content);
            String urdu = node.has("urdu") ? node.get("urdu").asText() : getFallbackAdviceUrdu();
            String english = node.has("english") ? node.get("english").asText() : getFallbackAdviceEnglish();
            return new String[]{urdu, english};
        } catch (Exception e) {
            log.warn("Farming advice failed ({}), using template", e.getMessage());
            return new String[]{getFallbackAdviceUrdu(), getFallbackAdviceEnglish()};
        }
    }

    // ══════════════════════════════════════════════════════════
    // TEMPLATE FALLBACKS (last resort — always available)
    // ══════════════════════════════════════════════════════════

    private String getFallbackUrdu() {
        return "یہ سمجھ نہیں آیا۔ پوچھ سکتے ہیں: 'گندم کی قیمت'، 'فصل بیچنی ہے'، یا 'والیٹ دکھاؤ'";
    }

    private String getFallbackEnglish() {
        return "I didn't understand that. Try asking: 'wheat price', 'sell my crop', or 'show wallet'";
    }

    private Map<String, String> getFallbackTip() {
        return Map.of(
                "titleUr", "گندم کی قیمت",
                "titleEn", "Wheat Market Update",
                "tipUr", "اس ہفتے گندم کی مانگ زیادہ ہے۔ اچھی قیمت مل سکتی ہے۔",
                "tipEn", "Wheat demand is high this week. You may get a good price.",
                "category", "market"
        );
    }

    private String getFallbackAdviceUrdu() {
        return "آپ کی فصل کے لیے موجودہ مارکیٹ ریٹ اور موسم کو مدنظر رکھتے ہوئے، " +
               "مقامی منڈی میں تازہ قیمتیں چیک کریں اور مناسب وقت پر فروخت کریں۔";
    }

    private String getFallbackAdviceEnglish() {
        return "Considering current market rates and season, check your local mandi " +
               "for fresh prices and sell at the right time.";
    }

    private String getTextSafe(JsonNode node, String field, String fallback) {
        return node.has(field) ? node.get(field).asText(fallback) : fallback;
    }
}