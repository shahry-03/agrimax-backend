package com.agrimax.ai.service;

import com.agrimax.ai.cache.GeminiResponseCache;
import com.agrimax.ai.session.ConversationSession;
import com.agrimax.ai.session.ConversationSessionManager;
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
 *
 * <p>Improvements over v1:</p>
 * <ul>
 *   <li>Conversation memory — sends session history to Gemini for contextual responses</li>
 *   <li>Response caching — identical transcripts skip Gemini API call</li>
 *   <li>Richer Urdu vocabulary in system prompt</li>
 *   <li>Falls back to smart keyword-based parser if Gemini is unavailable</li>
 * </ul>
 */
@Slf4j
@Service
public class IntentDetectionService {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String model;
    private final GeminiResponseCache cache;
    private final ConversationSessionManager sessionManager;

    // ── System Prompt — Rich Urdu/English Agriculture Context ──
    private static final String SYSTEM_PROMPT = """
            You are AgriMax AI, an intelligent voice assistant for Pakistani farmers.
            You help farmers with crop prices, selling crops, managing their wallet, and agricultural advice.
            
            Given a farmer's spoken text (Urdu Roman, Urdu script, or English), extract:
            1. intent — MUST be exactly one of:
               LOGIN, CREATE_SELL_POST, GET_CROP_PRICE, VIEW_PAYMENTS,
               VIEW_WALLET, PLACE_BID, CHECK_ORDER_STATUS, GET_RECOMMENDATION,
               UPDATE_PROFILE, VIEW_NOTIFICATIONS, UNKNOWN
            2. parameters — JSON object with relevant fields:
               - LOGIN: {phone: "03001234567"}
               - CREATE_SELL_POST: {crop: "wheat", quantity: 50, unit: "kg", price: 3000}
               - GET_CROP_PRICE: {crop: "wheat"}
               - VIEW_WALLET/VIEW_PAYMENTS: {}
               - GET_RECOMMENDATION: {crop: "wheat", topic: "selling|planting|weather"}
               - UNKNOWN: {raw_text: "the original text"}
            3. language — "ur" if Urdu/Roman Urdu, "en" if English
            4. confidence — 0.0 to 1.0
            
            === Urdu Crop Names (Roman) ===
            gehu/gandum=wheat, chawal=rice, makki/maize=corn, kapas=cotton,
            ganna=sugarcane, sarson=mustard, chana=chickpea, masoor=lentil,
            aam=mango, aloo=potato, pyaz=onion, tamatar=tomato,
            moong=mung bean, urad=black lentil, bajra=millet, jowar=sorghum
            
            === Urdu Commands (Roman) ===
            bechna=sell, khareedna=buy, keemat/qeemat/bhav/daam/rate=price,
            login/daakhil/lagan=login, wallet/batta=wallet,
            paisa/paisay/balance=money/balance, transaction/len-den=transaction,
            dikhao/batao/check=show/tell/check, meri fasal=my crop,
            aaj/kal=today/tomorrow, market/mandi=market
            
            === Response Rules ===
            - ALWAYS respond ONLY with valid JSON. No markdown, no explanations.
            - If the farmer mentions a crop without explicit intent, infer GET_CROP_PRICE
            - If text is incomplete/unclear, use UNKNOWN with confidence < 0.5
            - Consider conversation history for context (e.g., follow-up questions)
            
            === Example ===
            Input: "Mujhe 50 kilo gehu 3000 mein bechna hai"
            Output: {"intent":"CREATE_SELL_POST","parameters":{"crop":"wheat","quantity":50,"unit":"kg","price":3000},"language":"ur","confidence":0.95}
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
            Map.entry("tamatar", "tomato"), Map.entry("tomato", "tomato"),
            Map.entry("moong", "mung bean"), Map.entry("bajra", "millet"),
            Map.entry("jowar", "sorghum"), Map.entry("urad", "black lentil")
    );

    private static final Pattern PHONE_PATTERN = Pattern.compile("(03\\d{9})");
    private static final Pattern NUMBER_PATTERN = Pattern.compile("(\\d+)");

    public IntentDetectionService(
            @Value("${agrimax.ai.gemini.api-key}") String apiKey,
            @Value("${agrimax.ai.gemini.base-url}") String baseUrl,
            @Value("${agrimax.ai.gemini.model}") String model,
            ObjectMapper objectMapper,
            GeminiResponseCache cache,
            ConversationSessionManager sessionManager) {
        this.model = model;
        this.objectMapper = objectMapper;
        this.cache = cache;
        this.sessionManager = sessionManager;
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("x-goog-api-key", apiKey)
                .build();
    }

    /**
     * Detects intent from transcript, using cache and conversation history.
     *
     * @param transcript The user's spoken text
     * @param sessionId  User ID or "anonymous" for unauthenticated users
     */
    public VoiceIntent detectIntent(String transcript, String sessionId) {
        log.info("Detecting intent for: \"{}\" (session: {})", transcript, sessionId);

        // 1. Check cache first (only for simple/stateless queries)
        String cacheKey = transcript.trim().toLowerCase();
        VoiceIntent cached = cache.get(cacheKey);
        if (cached != null) {
            log.info("✅ Cache HIT — skipping Gemini API call");
            return cached;
        }

        // 2. Get conversation session for memory
        ConversationSession session = sessionManager.getOrCreate(sessionId);

        try {
            // 3. Build request with conversation history
            Map<String, Object> requestBody = buildGeminiRequest(transcript, session);

            String responseJson = restClient.post()
                    .uri("/models/{model}:generateContent", model)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            VoiceIntent intent = parseGeminiResponse(responseJson);
            log.info("✅ Gemini detected: {} (confidence: {}, lang: {})",
                    intent.intent(), intent.confidence(), intent.language());

            // 4. Update conversation history
            session.addUserTurn(transcript);
            session.addAssistantTurn(intent.intent() + ": " + intent.parameters());

            // 5. Cache result (only for high-confidence, parameter-independent intents)
            if (intent.confidence() >= 0.8 && isCacheable(intent)) {
                cache.put(cacheKey, intent);
            }

            return intent;

        } catch (Exception e) {
            log.warn("⚠️ Gemini API unavailable ({}), using smart fallback", e.getMessage());
            return smartFallback(transcript);
        }
    }

    /** Legacy overload for backward compatibility — uses "anonymous" session. */
    public VoiceIntent detectIntent(String transcript) {
        return detectIntent(transcript, "anonymous");
    }

    // ── Request Building ──

    private Map<String, Object> buildGeminiRequest(String transcript, ConversationSession session) {
        return Map.of(
                "system_instruction", Map.of(
                        "parts", List.of(Map.of("text", SYSTEM_PROMPT))
                ),
                "contents", session.buildContents(transcript),
                "generationConfig", Map.of(
                        "temperature", 0.05,       // Very low for consistent intent detection
                        "maxOutputTokens", 256,    // Intent JSON is small
                        "responseMimeType", "application/json"
                )
        );
    }

    @SuppressWarnings("unchecked")
    private VoiceIntent parseGeminiResponse(String responseJson) throws Exception {
        JsonNode root = objectMapper.readTree(responseJson);
        String content = root.at("/candidates/0/content/parts/0/text").asText();

        // Strip markdown code blocks if Gemini wraps in ```json ... ```
        content = content.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();

        JsonNode intentNode = objectMapper.readTree(content);

        String intent = intentNode.get("intent").asText("UNKNOWN");
        Map<String, Object> params = intentNode.has("parameters")
                ? objectMapper.convertValue(intentNode.get("parameters"), HashMap.class)
                : new HashMap<>();
        String language = intentNode.has("language") ? intentNode.get("language").asText() : "ur";
        double confidence = intentNode.has("confidence") ? intentNode.get("confidence").asDouble() : 0.8;

        return new VoiceIntent(intent, params, language, confidence);
    }

    /** Only cache intents that don't depend on user-specific context. */
    private boolean isCacheable(VoiceIntent intent) {
        return switch (intent.intent()) {
            case "GET_CROP_PRICE", "GET_RECOMMENDATION" -> true;
            default -> false;
        };
    }

    // ══════════════════════════════════════════
    // SMART FALLBACK — keyword + regex parser
    // ══════════════════════════════════════════

    private VoiceIntent smartFallback(String transcript) {
        String lower = transcript.toLowerCase().trim();

        if (matches(lower, "login", "daakhil", "log in", "sign in", "lagan")) {
            Map<String, Object> params = new HashMap<>();
            Matcher phoneMatcher = PHONE_PATTERN.matcher(transcript);
            if (phoneMatcher.find()) params.put("phone", phoneMatcher.group(1));
            return new VoiceIntent("LOGIN", params, detectLang(lower), 0.85);
        }

        if (matches(lower, "bech", "sell", "fasal bech", "list", "bechna")) {
            Map<String, Object> params = new HashMap<>();
            extractCrop(lower, params);
            extractNumbers(lower, params);
            return new VoiceIntent("CREATE_SELL_POST", params, detectLang(lower), 0.80);
        }

        if (matches(lower, "price", "keemat", "qeemat", "rate", "bhav", "daam", "kimat", "kitni hai")) {
            Map<String, Object> params = new HashMap<>();
            extractCrop(lower, params);
            return new VoiceIntent("GET_CROP_PRICE", params, detectLang(lower), 0.85);
        }

        if (matches(lower, "wallet", "balance", "paisa kitna", "paisay", "batta", "paisa dikhao")) {
            return new VoiceIntent("VIEW_WALLET", Map.of(), detectLang(lower), 0.85);
        }

        if (matches(lower, "payment", "transaction", "len den", "lein dein", "history", "transactions")) {
            return new VoiceIntent("VIEW_PAYMENTS", Map.of(), detectLang(lower), 0.85);
        }

        if (matches(lower, "recommend", "advice", "salah", "mashwara", "kya karoon", "kab bechoon")) {
            Map<String, Object> params = new HashMap<>();
            extractCrop(lower, params);
            return new VoiceIntent("GET_RECOMMENDATION", params, detectLang(lower), 0.75);
        }

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
        Matcher m = NUMBER_PATTERN.matcher(text);
        List<Integer> numbers = new ArrayList<>();
        while (m.find()) {
            try { numbers.add(Integer.parseInt(m.group(1))); }
            catch (NumberFormatException ignored) {}
        }

        if (numbers.size() >= 2) {
            int a = numbers.get(0), b = numbers.get(1);
            if (a < b) { params.put("quantity", a); params.put("price", b); }
            else { params.put("quantity", b); params.put("price", a); }
        } else if (numbers.size() == 1) {
            params.put("quantity", numbers.get(0));
        }

        if (text.contains("kilo") || text.contains("kg")) params.put("unit", "kg");
        else if (text.contains("mann") || text.contains("maund")) params.put("unit", "maund");
        else if (text.contains("ton")) params.put("unit", "ton");
        else if (text.contains("quintal")) params.put("unit", "quintal");
    }

    private String detectLang(String text) {
        if (matches(text, "mujhe", "meri", "karo", "hai", "kya", "bhai", "bech",
                "dikhao", "batao", "keemat", "fasal", "aaj", "mera", "kal",
                "chahiye", "hona", "kaisa", "kitna", "kahan")) {
            return "ur";
        }
        return "en";
    }
}
