package com.agrimax.ai.service;

import com.agrimax.ai.cache.GeminiResponseCache;
import com.agrimax.ai.service.LanguageDetector.DetectionResult;
import com.agrimax.common.session.ConversationSession;
import com.agrimax.common.session.ConversationSessionManager;
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
 * Calls Gemini API to detect intent from natural language text.
 * Auto-detects user language (Urdu / Roman Urdu / English) — no manual hints required.
 */
@Slf4j
@Service
public class IntentDetectionService {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String model;
    private final GeminiResponseCache cache;
    private final ConversationSessionManager sessionManager;
    private final LanguageDetector languageDetector;

    // ══════════════════════════════════════════════════════════
    // SYSTEM PROMPT
    // ══════════════════════════════════════════════════════════
    private static final String SYSTEM_PROMPT = """
            You are AgriMax AI, an intelligent voice assistant for Pakistani farmers.
            You help farmers with crop prices, selling crops, managing their wallet,
            getting AI-powered farming advice, AND navigating the mobile app.

            The user may speak in ENGLISH, URDU (script), or ROMAN URDU.
            The detected language is provided in the user message — respect it.

            Extract:
            1. intent — MUST be exactly one of:

               === ACTION INTENTS ===
               LOGIN, VERIFY_OTP, CREATE_SELL_POST, GET_CROP_PRICE, VIEW_PAYMENTS,
               VIEW_WALLET, PLACE_BID, CHECK_ORDER_STATUS, GET_RECOMMENDATION,
               UPDATE_PROFILE, VIEW_NOTIFICATIONS,

               === NAVIGATION INTENTS ===
               NAVIGATE, GO_BACK, HELP, UNKNOWN

            2. parameters — JSON object:
               - LOGIN: {phone: "03001234567"}
               - VERIFY_OTP: {otp: "1234"}
               - CREATE_SELL_POST: {crop: "wheat", quantity: 50, unit: "kg", price: 3000}
               - GET_CROP_PRICE: {crop: "wheat"}
               - VIEW_WALLET / VIEW_PAYMENTS: {}
               - GET_RECOMMENDATION: {crop: "wheat" or null,
                   topic: "selling"|"planting"|"weather"|"pest"|"fertilizer"|"general"}
               - NAVIGATE: {target: "/route", label: "Page Name"}
                 • home, ghar, dashboard → /
                 • market, marketplace, mandi → /marketplace
                 • prices, rates → /prices
                 • wallet, batta → /wallet
                 • profile, account → /profile
                 • settings → /settings
                 • my posts → /my-posts
               - GO_BACK: {}
               - HELP: {}
               - UNKNOWN: {raw_text: "original"}

            3. language — "ur" or "en"
            4. confidence — 0.0 to 1.0

            === Urdu Crop Names (Roman) ===
            gehu/gandum=wheat, chawal=rice, makki/maize=corn, kapas=cotton,
            ganna=sugarcane, sarson=mustard, chana=chickpea, masoor=lentil,
            aam=mango, aloo=potato, pyaz=onion, tamatar=tomato,
            moong=mung bean, urad=black lentil, bajra=millet, jowar=sorghum

            === Disambiguation ===
            - "wallet dikhao" → VIEW_WALLET (not navigate)
            - "wallet kholo" → NAVIGATE /wallet
            - "gehu ki keemat" → GET_CROP_PRICE
            - "gehu kab bechni" → GET_RECOMMENDATION topic=selling
            - "market kholo" → NAVIGATE /marketplace

            === Rules ===
            - Reply ONLY with valid JSON.
            - Use English tokens for crop names and units.
            - Preserve the user's spoken language in the "language" field.

            === Examples ===
            Input: "Meri gehu ki fasal kab bechni chahiye?"
            Output: {"intent":"GET_RECOMMENDATION","parameters":{"crop":"wheat","topic":"selling"},"language":"ur","confidence":0.95}

            Input: "When should I sell my wheat crop?"
            Output: {"intent":"GET_RECOMMENDATION","parameters":{"crop":"wheat","topic":"selling"},"language":"en","confidence":0.95}

            Input: "Market kholo"
            Output: {"intent":"NAVIGATE","parameters":{"target":"/marketplace","label":"Marketplace"},"language":"ur","confidence":0.95}

            Input: "Open the wallet page"
            Output: {"intent":"NAVIGATE","parameters":{"target":"/wallet","label":"Wallet"},"language":"en","confidence":0.95}

            Input: "Mujhe 50 kilo gehu 3000 mein bechna hai"
            Output: {"intent":"CREATE_SELL_POST","parameters":{"crop":"wheat","quantity":50,"unit":"kg","price":3000},"language":"ur","confidence":0.95}
            """;

    // Crop map
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

    private static final Map<String, String[]> NAV_KEYWORDS = Map.ofEntries(
            Map.entry("/", new String[]{"home", "ghar", "main page", "dashboard", "homepage"}),
            Map.entry("/marketplace", new String[]{"market", "marketplace", "mandi", "bazaar"}),
            Map.entry("/prices", new String[]{"prices", "price list", "rates", "mandi rates"}),
            Map.entry("/wallet", new String[]{"wallet page", "wallet pe", "wallet par", "wallet screen"}),
            Map.entry("/profile", new String[]{"profile", "meri profile", "my profile", "account"}),
            Map.entry("/settings", new String[]{"settings", "setting", "preferences"}),
            Map.entry("/my-posts", new String[]{"my posts", "meri posts", "meri listings"})
    );

    private static final Map<String, String> NAV_LABELS = Map.of(
            "/", "Home",
            "/marketplace", "Marketplace",
            "/prices", "Prices",
            "/wallet", "Wallet",
            "/profile", "Profile",
            "/settings", "Settings",
            "/my-posts", "My Posts"
    );

    private static final Pattern PHONE_PATTERN = Pattern.compile("(03\\d{9})");
    private static final Pattern NUMBER_PATTERN = Pattern.compile("(\\d+)");
    private static final Pattern OTP_PATTERN = Pattern.compile("\\b(\\d{4,6})\\b");

    // Session keys
    private static final String KEY_LAST_INTENT = "lastIntent";
    private static final String KEY_SESSION_LANG = "sessionLanguage";

    public IntentDetectionService(
            @Value("${agrimax.ai.gemini.api-key}") String apiKey,
            @Value("${agrimax.ai.gemini.base-url}") String baseUrl,
            @Value("${agrimax.ai.gemini.model}") String model,
            ObjectMapper objectMapper,
            GeminiResponseCache cache,
            ConversationSessionManager sessionManager,
            LanguageDetector languageDetector) {
        this.model = model;
        this.objectMapper = objectMapper;
        this.cache = cache;
        this.sessionManager = sessionManager;
        this.languageDetector = languageDetector;
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("x-goog-api-key", apiKey)
                .build();
    }

    // ══════════════════════════════════════════════════════════
    // PUBLIC API
    // ══════════════════════════════════════════════════════════

    public VoiceIntent detectIntent(String transcript, String sessionId) {
        log.info("Detecting intent for: \"{}\" (session: {})", transcript, sessionId);

        // ⭐ STEP 1: Auto-detect language from transcript
        DetectionResult langResult = languageDetector.detect(transcript);
        log.info("🌐 Detected language: {} (confidence: {}, reason: {})",
                langResult.language(), langResult.confidence(), langResult.reason());

        // ⭐ STEP 2: Session-sticky language (once established, stick to it)
        ConversationSession session = sessionManager.getOrCreate(sessionId);
        String establishedLang = (String) session.getContext().get(KEY_SESSION_LANG);

        // Decide final language: high-confidence detection wins; otherwise use session
        String effectiveLang;
        if (langResult.confidence() >= 0.75) {
            effectiveLang = langResult.language();
            session.getContext().put(KEY_SESSION_LANG, effectiveLang); // update sticky
        } else if (establishedLang != null) {
            effectiveLang = establishedLang;
            log.debug("Using session-established language: {}", effectiveLang);
        } else {
            effectiveLang = langResult.language();
            session.getContext().put(KEY_SESSION_LANG, effectiveLang);
        }

        // ⭐ STEP 3: Multi-turn flow check (OTP/phone during LOGIN flow)
        VoiceIntent multiTurn = checkMultiTurnFlow(transcript, session, effectiveLang);
        if (multiTurn != null) {
            log.info("🔗 Multi-turn flow intercepted → {}", multiTurn.intent());
            return multiTurn;
        }

        // ⭐ STEP 4: Cache check
        String cacheKey = transcript.trim().toLowerCase();
        VoiceIntent cached = cache.get(cacheKey);
        if (cached != null) {
            log.info("✅ Cache HIT — skipping Gemini API call");
            // Re-stamp with fresh language (may differ from cache)
            return new VoiceIntent(cached.intent(), cached.parameters(), effectiveLang, cached.confidence());
        }

        // ⭐ STEP 5: Gemini call
        try {
            Map<String, Object> requestBody = buildGeminiRequest(transcript, session, effectiveLang);

            String responseJson = restClient.post()
                    .uri("/models/{model}:generateContent", model)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            VoiceIntent intent = parseGeminiResponse(responseJson);

            // Override language if our detector is more confident
            if (langResult.confidence() > 0.75) {
                intent = new VoiceIntent(intent.intent(), intent.parameters(),
                        effectiveLang, intent.confidence());
            }

            log.info("✅ Gemini detected: {} (confidence: {}, lang: {})",
                    intent.intent(), intent.confidence(), intent.language());

            session.addUserTurn(transcript);
            session.addAssistantTurn(intent.intent() + ": " + intent.parameters());
            session.getContext().put(KEY_LAST_INTENT, intent.intent());

            if (intent.confidence() >= 0.8 && isCacheable(intent)) {
                cache.put(cacheKey, intent);
            }

            return intent;

        } catch (Exception e) {
            log.warn("⚠️ Gemini unavailable ({}), using smart fallback", e.getMessage());
            VoiceIntent fallback = smartFallback(transcript, effectiveLang);
            session.getContext().put(KEY_LAST_INTENT, fallback.intent());
            return fallback;
        }
    }

    public VoiceIntent detectIntent(String transcript) {
        return detectIntent(transcript, "anonymous");
    }

    // ══════════════════════════════════════════════════════════
    // MULTI-TURN FLOW (LOGIN → phone → OTP)
    // ══════════════════════════════════════════════════════════

    /**
     * If the user is mid-way through a multi-turn flow (e.g., they said "login",
     * we asked for phone number, and now they're giving us a phone number),
     * intercept the input and route to the correct intent.
     */
    private VoiceIntent checkMultiTurnFlow(String transcript, ConversationSession session, String lang) {
        Object lastIntentObj = session.getContext().get(KEY_LAST_INTENT);
        if (lastIntentObj == null) return null;
        String lastIntent = lastIntentObj.toString();

        // Flow: LOGIN → expect phone number
        if ("LOGIN".equals(lastIntent)) {
            Matcher phone = PHONE_PATTERN.matcher(transcript);
            if (phone.find()) {
                Map<String, Object> params = new HashMap<>();
                params.put("phone", phone.group(1));
                session.getContext().remove(KEY_LAST_INTENT); // clear flow
                return new VoiceIntent("LOGIN", params, lang, 0.98);
            }
            // User might say "never mind" or provide random text — clear flow
            if (!transcript.matches(".*\\d.*")) {
                // No digits: user is not giving phone. Let normal flow continue.
                session.getContext().remove(KEY_LAST_INTENT);
            }
        }

        // Flow: LOGIN succeeded → expect OTP
        if ("VERIFY_OTP".equals(lastIntent) || session.getContext().containsKey("awaitingOtp")) {
            Matcher otp = OTP_PATTERN.matcher(transcript);
            if (otp.find()) {
                Map<String, Object> params = new HashMap<>();
                params.put("otp", otp.group(1));
                session.getContext().remove("awaitingOtp");
                session.getContext().remove(KEY_LAST_INTENT);
                return new VoiceIntent("VERIFY_OTP", params, lang, 0.98);
            }
        }

        return null;
    }

    // ══════════════════════════════════════════════════════════
    // GEMINI REQUEST/RESPONSE
    // ══════════════════════════════════════════════════════════

    private Map<String, Object> buildGeminiRequest(String transcript, ConversationSession session, String detectedLang) {
        // Pass detected language to Gemini for context
        String langHint = "ur".equals(detectedLang)
                ? "\n[Detected user language: URDU — respond with language=\"ur\"]"
                : "\n[Detected user language: ENGLISH — respond with language=\"en\"]";

        return Map.of(
                "system_instruction", Map.of(
                        "parts", List.of(Map.of("text", SYSTEM_PROMPT + langHint))
                ),
                "contents", session.buildContents(transcript),
                "generationConfig", Map.of(
                        "temperature", 0.05,
                        "maxOutputTokens", 350,
                        "responseMimeType", "application/json"
                )
        );
    }

    @SuppressWarnings("unchecked")
    private VoiceIntent parseGeminiResponse(String responseJson) throws Exception {
        JsonNode root = objectMapper.readTree(responseJson);
        String content = root.at("/candidates/0/content/parts/0/text").asText();
        content = content.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
        content = content.replaceAll("(\\d)\\.([,}\\]])", "$1.0$2");
        content = content.replaceAll("(\\d+\\.\\d+)\\.", "$1");
        content = content.replaceAll(":(\\.\\d)", ":0$1");

        JsonNode intentNode = objectMapper.readTree(content);

        String intent = intentNode.get("intent").asText("UNKNOWN");
        Map<String, Object> params = intentNode.has("parameters")
                ? objectMapper.convertValue(intentNode.get("parameters"), HashMap.class)
                : new HashMap<>();
        String language = intentNode.has("language") ? intentNode.get("language").asText() : "ur";
        double confidence = intentNode.has("confidence") ? intentNode.get("confidence").asDouble() : 0.8;

        return new VoiceIntent(intent, params, language, confidence);
    }

    private boolean isCacheable(VoiceIntent intent) {
        return switch (intent.intent()) {
            case "GET_CROP_PRICE", "GET_RECOMMENDATION", "NAVIGATE", "HELP", "GO_BACK" -> true;
            default -> false;
        };
    }

    // ══════════════════════════════════════════════════════════
    // SMART FALLBACK (offline keyword parser)
    // ══════════════════════════════════════════════════════════

    private VoiceIntent smartFallback(String transcript, String detectedLang) {
        String lower = transcript.toLowerCase().trim();
        String lang = detectedLang != null ? detectedLang : "ur";

        // HELP
        if (matches(lower, "help", "madad", "kya kar sakte", "what can you do",
                    "commands", "kya kya kar")) {
            return new VoiceIntent("HELP", Map.of(), lang, 0.85);
        }

        // GO_BACK
        if (matches(lower, "wapas", "back", "go back", "previous page", "pichla page")) {
            return new VoiceIntent("GO_BACK", Map.of(), lang, 0.85);
        }

        // LOGIN
        if (matches(lower, "login", "daakhil", "log in", "sign in", "lagan")) {
            Map<String, Object> params = new HashMap<>();
            Matcher phoneMatcher = PHONE_PATTERN.matcher(transcript);
            if (phoneMatcher.find()) params.put("phone", phoneMatcher.group(1));
            return new VoiceIntent("LOGIN", params, lang, 0.85);
        }

        // VERIFY_OTP
        if (matches(lower, "otp", "code", "pin", "otp batao", "verification")) {
            Map<String, Object> params = new HashMap<>();
            Matcher otpMatcher = OTP_PATTERN.matcher(transcript);
            if (otpMatcher.find()) params.put("otp", otpMatcher.group(1));
            return new VoiceIntent("VERIFY_OTP", params, lang, 0.85);
        }

        // GET_RECOMMENDATION
        if (matches(lower, "recommend", "advice", "salah", "mashwara",
                    "kab bechni", "kab bechoon", "when should i sell",
                    "kab bonay", "when to plant", "kab lagana",
                    "mausam", "weather", "barish", "rain",
                    "keeray", "pest", "bimari", "disease",
                    "khaad", "fertilizer",
                    "kya karoon", "what should i do")) {
            Map<String, Object> params = new HashMap<>();
            extractCrop(lower, params);
            params.put("topic", detectRecommendationTopic(lower));
            return new VoiceIntent("GET_RECOMMENDATION", params, lang, 0.85);
        }

        // NAVIGATE
        if (matches(lower, "kholo", "open", "jao", "go to", "chalein", "chalo", "page")) {
            String target = detectNavigationTarget(lower);
            if (target != null) {
                Map<String, Object> params = new HashMap<>();
                params.put("target", target);
                params.put("label", NAV_LABELS.getOrDefault(target, "Page"));
                return new VoiceIntent("NAVIGATE", params, lang, 0.85);
            }
        }

        // CREATE_SELL_POST
        if (matches(lower, "bech", "sell", "fasal bech", "list", "bechna")) {
            Map<String, Object> params = new HashMap<>();
            extractCrop(lower, params);
            extractNumbers(lower, params);
            return new VoiceIntent("CREATE_SELL_POST", params, lang, 0.80);
        }

        // GET_CROP_PRICE
        if (matches(lower, "price", "keemat", "qeemat", "rate", "bhav", "daam",
                    "kimat", "kitni hai", "kitna hai")) {
            Map<String, Object> params = new HashMap<>();
            extractCrop(lower, params);
            return new VoiceIntent("GET_CROP_PRICE", params, lang, 0.85);
        }

        // VIEW_WALLET
        if (matches(lower, "wallet", "balance", "paisa kitna", "paisay", "batta",
                    "paisa dikhao", "mera paisa")) {
            return new VoiceIntent("VIEW_WALLET", Map.of(), lang, 0.85);
        }

        // VIEW_PAYMENTS
        if (matches(lower, "payment", "transaction", "len den", "lein dein",
                    "history", "transactions", "paisay ka hisaab")) {
            return new VoiceIntent("VIEW_PAYMENTS", Map.of(), lang, 0.85);
        }

        log.warn("Could not detect intent for: \"{}\"", transcript);
        return new VoiceIntent("UNKNOWN", Map.of("raw_text", transcript), lang, 0.3);
    }

    private String detectRecommendationTopic(String lower) {
        if (matches(lower, "bech", "sell", "keemat kya", "rate kya",
                    "kab bechni", "kab bechoon", "when should i sell")) return "selling";
        if (matches(lower, "bonay", "bonai", "plant", "lagana", "boai", "beej",
                    "when to plant")) return "planting";
        if (matches(lower, "mausam", "weather", "barish", "rain", "garmi", "sardi")) return "weather";
        if (matches(lower, "keeray", "keera", "pest", "bimari", "disease", "kida")) return "pest";
        if (matches(lower, "khaad", "fertilizer", "urea", "dap", "npk")) return "fertilizer";
        return "general";
    }

    private String detectNavigationTarget(String lower) {
        for (Map.Entry<String, String[]> entry : NAV_KEYWORDS.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (lower.contains(keyword)) return entry.getKey();
            }
        }
        return null;
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
}