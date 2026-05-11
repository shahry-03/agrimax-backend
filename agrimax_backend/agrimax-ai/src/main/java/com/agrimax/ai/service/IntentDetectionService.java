package com.agrimax.ai.service;

import com.agrimax.common.voice.VoiceIntent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Calls Gemini API to detect intent from natural language text (Urdu/English).
 * Falls back to a smart keyword-based parser if Gemini is unavailable.
 */
@Slf4j
@Service
public class IntentDetectionService {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String model;

    private static final String SYSTEM_PROMPT = """
            You are an intent detection engine for AgriMax, a Pakistani agricultural marketplace.
            Given a farmer's text (Urdu or English), extract:
            1. intent — one of: LOGIN, CREATE_SELL_POST, GET_CROP_PRICE, VIEW_PAYMENTS, VIEW_WALLET, PLACE_BID, CHECK_ORDER_STATUS, GET_RECOMMENDATION, UPDATE_PROFILE, VIEW_NOTIFICATIONS
            2. parameters — JSON object with relevant fields (phone, crop, quantity, unit, price, etc.)
            3. language — "ur" or "en"
            4. confidence — 0.0 to 1.0
            
            Common Urdu crop names: gehu=wheat, chawal=rice, makki=corn, kapas=cotton, ganna=sugarcane, sarson=mustard, chana=chickpea, masoor=lentil, aam=mango, aloo=potato, pyaz=onion, tamatar=tomato
            Common Urdu commands: bechna=sell, khareedna=buy, keemat/price=price, login/daakhil=login, payment/paisa=payment
            
            Respond ONLY with valid JSON. No markdown, no explanation.
            Example: {"intent":"CREATE_SELL_POST","parameters":{"crop":"wheat","quantity":50,"unit":"kg","price":3000},"language":"ur","confidence":0.95}
            """;

    // ── Urdu crop name → English mapping ──
    private static final Map<String, String> CROP_MAP = Map.ofEntries(
            Map.entry("gehu", "wheat"), Map.entry("gandum", "wheat"), Map.entry("wheat", "wheat"),
            Map.entry("chawal", "rice"), Map.entry("rice", "rice"),
            Map.entry("makki", "corn"), Map.entry("corn", "corn"), Map.entry("maize", "corn"),
            Map.entry("kapas", "cotton"), Map.entry("cotton", "cotton"),
            Map.entry("ganna", "sugarcane"), Map.entry("sugarcane", "sugarcane"),
            Map.entry("sarson", "mustard"), Map.entry("mustard", "mustard"),
            Map.entry("chana", "chickpea"), Map.entry("chickpea", "chickpea"),
            Map.entry("masoor", "lentil"), Map.entry("lentil", "lentil"),
            Map.entry("aam", "mango"), Map.entry("mango", "mango"),
            Map.entry("aloo", "potato"), Map.entry("potato", "potato"),
            Map.entry("pyaz", "onion"), Map.entry("onion", "onion"),
            Map.entry("tamatar", "tomato"), Map.entry("tomato", "tomato")
    );

    private static final Pattern PHONE_PATTERN = Pattern.compile("(03\\d{9})");
    private static final Pattern NUMBER_PATTERN = Pattern.compile("(\\d+)");

    public IntentDetectionService(
            @Value("${agrimax.ai.gemini.api-key}") String apiKey,
            @Value("${agrimax.ai.gemini.base-url}") String baseUrl,
            @Value("${agrimax.ai.gemini.model}") String model,
            ObjectMapper objectMapper) {
        this.model = model;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("x-goog-api-key", apiKey)
                .build();
    }

    public VoiceIntent detectIntent(String transcript) {
        log.info("Detecting intent for: \"{}\"", transcript);

        try {
            Map<String, Object> requestBody = buildGeminiRequest(transcript);

            String responseJson = restClient.post()
                    .uri("/models/{model}:generateContent", model)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            VoiceIntent intent = parseGeminiResponse(responseJson);
            log.info("✅ Gemini detected: {} (confidence: {})", intent.intent(), intent.confidence());
            return intent;

        } catch (Exception e) {
            log.warn("⚠️ Gemini API unavailable ({}), using smart fallback", e.getMessage());
            return smartFallback(transcript);
        }
    }

    private Map<String, Object> buildGeminiRequest(String transcript) {
        return Map.of(
                "system_instruction", Map.of(
                        "parts", List.of(Map.of("text", SYSTEM_PROMPT))
                ),
                "contents", List.of(
                        Map.of("parts", List.of(Map.of("text", transcript)))
                ),
                "generationConfig", Map.of(
                        "temperature", 0.1,
                        "responseMimeType", "application/json"
                )
        );
    }

    @SuppressWarnings("unchecked")
    private VoiceIntent parseGeminiResponse(String responseJson) throws Exception {
        JsonNode root = objectMapper.readTree(responseJson);
        String content = root.at("/candidates/0/content/parts/0/text").asText();

        JsonNode intentNode = objectMapper.readTree(content);

        String intent = intentNode.get("intent").asText();
        Map<String, Object> params = objectMapper.convertValue(
                intentNode.get("parameters"), HashMap.class);
        String language = intentNode.has("language") ? intentNode.get("language").asText() : "ur";
        double confidence = intentNode.has("confidence") ? intentNode.get("confidence").asDouble() : 0.8;

        return new VoiceIntent(intent, params, language, confidence);
    }

    // ═══════════════════════════════════════════════
    // SMART FALLBACK — keyword + regex based parser
    // ═══════════════════════════════════════════════

    private VoiceIntent smartFallback(String transcript) {
        String lower = transcript.toLowerCase().trim();

        // 1. LOGIN intent
        if (matches(lower, "login", "daakhil", "log in", "sign in")) {
            Map<String, Object> params = new HashMap<>();
            Matcher phoneMatcher = PHONE_PATTERN.matcher(transcript);
            if (phoneMatcher.find()) {
                params.put("phone", phoneMatcher.group(1));
            }
            return new VoiceIntent("LOGIN", params, detectLang(lower), 0.85);
        }

        // 2. CREATE_SELL_POST intent
        if (matches(lower, "bech", "sell", "fasal bech", "list")) {
            Map<String, Object> params = new HashMap<>();
            extractCrop(lower, params);
            extractNumbers(lower, params);
            return new VoiceIntent("CREATE_SELL_POST", params, detectLang(lower), 0.80);
        }

        // 3. GET_CROP_PRICE intent
        if (matches(lower, "price", "keemat", "rate", "bhav", "daam", "kimat")) {
            Map<String, Object> params = new HashMap<>();
            extractCrop(lower, params);
            return new VoiceIntent("GET_CROP_PRICE", params, detectLang(lower), 0.85);
        }

        // 4. VIEW_WALLET intent
        if (matches(lower, "wallet", "balance", "paisa kitna", "paisay")) {
            return new VoiceIntent("VIEW_WALLET", Map.of(), detectLang(lower), 0.85);
        }

        // 5. VIEW_PAYMENTS intent
        if (matches(lower, "payment", "transaction", "len den", "lein dein", "paisa dikhao")) {
            return new VoiceIntent("VIEW_PAYMENTS", Map.of(), detectLang(lower), 0.85);
        }

        // Unknown
        log.warn("Could not detect intent for: \"{}\"", transcript);
        return new VoiceIntent("UNKNOWN", Map.of("raw_text", transcript), detectLang(lower), 0.3);
    }

    private boolean matches(String text, String... keywords) {
        for (String kw : keywords) {
            if (text.contains(kw)) return true;
        }
        return false;
    }

    private void extractCrop(String text, Map<String, Object> params) {
        for (Map.Entry<String, String> entry : CROP_MAP.entrySet()) {
            if (text.contains(entry.getKey())) {
                params.put("crop", entry.getValue());
                return;
            }
        }
    }

    private void extractNumbers(String text, Map<String, Object> params) {
        // Try to find patterns like "50 kilo ... 3000 mein" or "50 kg at 3000"
        Matcher m = NUMBER_PATTERN.matcher(text);
        List<Integer> numbers = new ArrayList<>();
        while (m.find()) {
            try {
                numbers.add(Integer.parseInt(m.group(1)));
            } catch (NumberFormatException ignored) {}
        }

        if (numbers.size() >= 2) {
            // Heuristic: smaller number = quantity, larger = price
            int a = numbers.get(0), b = numbers.get(1);
            if (a < b) {
                params.put("quantity", a);
                params.put("price", b);
            } else {
                params.put("quantity", b);
                params.put("price", a);
            }
        } else if (numbers.size() == 1) {
            params.put("quantity", numbers.get(0));
        }

        // Unit detection
        if (text.contains("kilo") || text.contains("kg")) {
            params.put("unit", "kg");
        } else if (text.contains("mann") || text.contains("maund")) {
            params.put("unit", "maund");
        } else if (text.contains("ton")) {
            params.put("unit", "ton");
        }
    }

    private String detectLang(String text) {
        // Simple heuristic: if contains common Urdu romanized words → "ur"
        if (matches(text, "mujhe", "meri", "karo", "hai", "kya", "bhai", "bech",
                "dikhao", "keemat", "fasal", "aaj")) {
            return "ur";
        }
        return "en";
    }
}
