package com.agrimax.ai.service;

import com.agrimax.ai.cache.GeminiResponseCache;
import com.agrimax.ai.llm.GroqLlmProvider;
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
    private final GroqLlmProvider groqLlmProvider;

    private static final String SYSTEM_PROMPT = """
        You are AgriMax AI, an intelligent voice assistant for Pakistani farmers.

        Extract:
        1. intent — MUST be exactly one of:

           === ACTION INTENTS ===
           LOGIN, VERIFY_OTP, CREATE_SELL_POST, GET_CROP_PRICE, VIEW_PAYMENTS,
           VIEW_WALLET, PLACE_BID, VIEW_MY_BIDS, ACCEPT_BID, COMPLETE_DEAL, CANCEL_DEAL,
           CHECK_ORDER_STATUS, GET_RECOMMENDATION, UPDATE_PROFILE, VIEW_NOTIFICATIONS,

           === NAVIGATION INTENTS ===
           NAVIGATE, GO_BACK, HELP, UNKNOWN,

           === SESSION CONTROL ===
           STOP_LISTENING

        2. parameters — JSON object:
           - LOGIN: {phone: "03001234567"}
           - VERIFY_OTP: {otp: "1234"}
           - CREATE_SELL_POST: {crop: "wheat", quantity: 50, unit: "kg", price: 3000}
           - GET_CROP_PRICE: {crop: "wheat"}
           - VIEW_WALLET / VIEW_PAYMENTS: {}
           - VIEW_MY_BIDS: {}
           - PLACE_BID: {bidPrice: 3200, message: "optional"}
             (postId is from frontend — DO NOT extract)
           - ACCEPT_BID: {} (bidId is from frontend — DO NOT extract)
           - COMPLETE_DEAL: {} (dealId is from frontend — DO NOT extract)
           - CANCEL_DEAL: {} (dealId is from frontend — DO NOT extract)
           - CHECK_ORDER_STATUS: {}
           - UPDATE_PROFILE: {name: "Ahmed"|null, languagePref: "ur"|"en"|null}
           - VIEW_NOTIFICATIONS: {}
           - GET_RECOMMENDATION: {crop: "wheat"|null, topic: "selling"|"planting"|"weather"|"pest"|"fertilizer"|"general"}
           - NAVIGATE: {target: "/route", label: "Page Name"}
             • home, ghar → /
             • market, marketplace, mandi → /marketplace
             • prices, rates → /prices
             • wallet, batta → /wallet
             • profile → /profile
             • my posts → /my-posts
             • notifications → /notifications
             • orders, deals, saude → /marketplace/deals
           - GO_BACK: {}
           - HELP: {}
           - STOP_LISTENING: {}
           - UNKNOWN: {raw_text: "original"}

        3. language — "ur" or "en"
        4. confidence — 0.0 to 1.0

        === Urdu Crop Names ===
        gehu/gandum=wheat, chawal=rice, makki/maize=corn, kapas=cotton,
        ganna=sugarcane, sarson=mustard, chana=chickpea, masoor=lentil,
        aam=mango, aloo=potato, pyaz=onion, tamatar=tomato,
        moong=mung bean, urad=black lentil, bajra=millet, jowar=sorghum

        === Disambiguation ===
        - "wallet dikhao" → VIEW_WALLET
        - "wallet kholo" → NAVIGATE /wallet
        - "gehu ki keemat" → GET_CROP_PRICE
        - "gehu kab bechni" → GET_RECOMMENDATION topic=selling
        - "is post pe 3200 ki bid lagao" → PLACE_BID {bidPrice: 3200}
        - "is bid ko accept karo" → ACCEPT_BID
        - "bid accept karo" → ACCEPT_BID
        - "delivery ho gayi" / "paise release karo" → COMPLETE_DEAL
        - "deal cancel karo" / "soda cancel" → CANCEL_DEAL
        - "mera naam Ahmed hai" → UPDATE_PROFILE {name: "Ahmed"}
        - "english mein baat karo" → UPDATE_PROFILE {languagePref: "en"}
        - "meri notifications" → VIEW_NOTIFICATIONS
        - "mere orders" → CHECK_ORDER_STATUS

        === Session Control ===
        User says "bas", "ruko", "band karo", "kaafi", "shukriya", "bye",
        "stop", "exit", "quit", "mic band", "aur nahi" → STOP_LISTENING {}

        === Rules ===
        - Reply ONLY with valid JSON.
        - Use English tokens for crops/units.
        - Preserve user's language.
        - ACCEPT_BID/COMPLETE_DEAL/CANCEL_DEAL: parameters = {} (IDs come from frontend).

        === Examples ===
        Input: "Mujhe 50 kilo gehu 3000 mein bechna hai"
        Output: {"intent":"CREATE_SELL_POST","parameters":{"crop":"wheat","quantity":50,"unit":"kg","price":3000},"language":"ur","confidence":0.95}

        Input: "Is bid ko accept karo"
        Output: {"intent":"ACCEPT_BID","parameters":{},"language":"ur","confidence":0.95}

        Input: "Delivery ho gayi, paise release karo"
        Output: {"intent":"COMPLETE_DEAL","parameters":{},"language":"ur","confidence":0.95}

        Input: "Deal cancel karo"
        Output: {"intent":"CANCEL_DEAL","parameters":{},"language":"ur","confidence":0.95}
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
            LanguageDetector languageDetector,
            GroqLlmProvider groqLlmProvider) {
        this.model = model;
        this.objectMapper = objectMapper;
        this.cache = cache;
        this.sessionManager = sessionManager;
        this.languageDetector = languageDetector;
        this.groqLlmProvider = groqLlmProvider;
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("x-goog-api-key", apiKey)
                .build();
    }

    // ══════════════════════════════════════════════════════════
    // PUBLIC API
    // ══════════════════════════════════════════════════════════

    /**
     * Backward-compatible overload for callers that do not provide a role.
     */
    public VoiceIntent detectIntent(String transcript, String sessionId) {
        return detectIntent(transcript, sessionId, null);
    }

    /**
     * Primary role-aware intent detection API.
     */
    public VoiceIntent detectIntent(String transcript, String sessionId, String role) {
        log.info("Detecting intent — transcript: \"{}\" | session: {} | role: {}",
                transcript, sessionId, role != null ? role : "ANONYMOUS");

        // STEP 1: Language detection
        DetectionResult langResult = languageDetector.detect(transcript);
        log.info("Language: {} (confidence: {}, reason: {})",
                langResult.language(), langResult.confidence(), langResult.reason());

        // STEP 2: Session-sticky language
        ConversationSession session = sessionManager.getOrCreate(sessionId);
        String establishedLang = (String) session.getContext().get(KEY_SESSION_LANG);

        String effectiveLang;
        if (langResult.confidence() >= 0.75) {
            effectiveLang = langResult.language();
            session.getContext().put(KEY_SESSION_LANG, effectiveLang);
        } else if (establishedLang != null) {
            effectiveLang = establishedLang;
        } else {
            effectiveLang = langResult.language();
            session.getContext().put(KEY_SESSION_LANG, effectiveLang);
        }

        // STEP 3: Multi-turn flow (OTP etc.)
        VoiceIntent multiTurn = checkMultiTurnFlow(transcript, session, effectiveLang);
        if (multiTurn != null) {
            log.info("Multi-turn flow -> {}", multiTurn.intent());
            return multiTurn;
        }

        // STEP 4: Role-aware cache
        String cacheKey = (role != null ? role.toUpperCase(Locale.ROOT) + "|" : "")
                + transcript.trim().toLowerCase(Locale.ROOT);
        VoiceIntent cached = cache.get(cacheKey);
        if (cached != null) {
            log.info("Cache HIT");
            return new VoiceIntent(cached.intent(), cached.parameters(),
                    effectiveLang, cached.confidence());
        }

        // STEP 5: Gemini call with role hint
        try {
            Map<String, Object> requestBody = buildGeminiRequest(
                    transcript, session, effectiveLang, role);

            String responseJson = restClient.post()
                    .uri("/models/{model}:generateContent", model)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            VoiceIntent intent = parseGeminiResponse(responseJson);

            if (langResult.confidence() > 0.75) {
                intent = new VoiceIntent(intent.intent(), intent.parameters(),
                        effectiveLang, intent.confidence());
            }

            log.info("Gemini: intent={} conf={} lang={}",
                    intent.intent(), intent.confidence(), intent.language());

            session.addUserTurn(transcript);
            session.addAssistantTurn(intent.intent() + ": " + intent.parameters());
            session.getContext().put(KEY_LAST_INTENT, intent.intent());

            if (intent.confidence() >= 0.8 && isCacheable(intent)) {
                cache.put(cacheKey, intent);
            }
            return intent;

        } catch (Exception e) {
            log.warn("Gemini unavailable ({}), trying Groq fallback", e.getMessage());

            VoiceIntent groqFallback = tryGroqFallback(
                    transcript, effectiveLang, role, session);
            if (groqFallback != null) {
                log.info("Groq fallback: {}", groqFallback.intent());
                session.addUserTurn(transcript);
                session.addAssistantTurn(
                        groqFallback.intent() + ": " + groqFallback.parameters());
                session.getContext().put(KEY_LAST_INTENT, groqFallback.intent());
                return groqFallback;
            }

            log.warn("Groq fallback failed, using smart fallback");
            VoiceIntent fallback = smartFallback(transcript, effectiveLang);
            session.getContext().put(KEY_LAST_INTENT, fallback.intent());
            return fallback;
        }
    }

    /**
     * Backward-compatible overload for callers that only provide a transcript.
     */
    public VoiceIntent detectIntent(String transcript) {
        return detectIntent(transcript, "anonymous", null);
    }

    /**
     * Groq fallback — used when the Gemini API fails.
     * Uses a single-shot request without session history.
     */
    private VoiceIntent tryGroqFallback(String transcript, String detectedLang,
                                         String role, ConversationSession session) {
        try {
            String langHint = "ur".equals(detectedLang)
                    ? "\n[Detected user language: URDU — respond with language=\"ur\"]"
                    : "\n[Detected user language: ENGLISH — respond with language=\"en\"]";

            String roleHint = buildRoleHint(role);

            String content = groqLlmProvider.chat(
                    SYSTEM_PROMPT + langHint + roleHint,
                    transcript,
                    0.05,
                    350
            );

            content = content
                    .replaceAll("```json\\s*", "")
                    .replaceAll("```\\s*", "")
                    .trim();

            // Apply the same JSON cleanup used for Gemini responses.
            content = content.replaceAll("(\\d)\\.([,}\\]])", "$1.0$2");
            content = content.replaceAll("(\\d+\\.\\d+)\\.", "$1");
            content = content.replaceAll(":(\\.\\d)", ":0$1");

            JsonNode intentNode = objectMapper.readTree(content);

            String intent = intentNode.path("intent").asText("UNKNOWN");
            Map<String, Object> params = intentNode.has("parameters")
                    && intentNode.get("parameters").isObject()
                    ? objectMapper.convertValue(intentNode.get("parameters"), HashMap.class)
                    : new HashMap<>();
            String language = intentNode.path("language").asText(detectedLang);
            double confidence = intentNode.path("confidence").asDouble(0.75);

            if (intent.isBlank()) {
                throw new IllegalArgumentException("Groq response did not contain a valid intent");
            }

            return new VoiceIntent(intent, params, language, confidence);

        } catch (Exception ex) {
            log.warn("Groq fallback failed: {}", ex.getMessage());
            return null;
        }
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

    private Map<String, Object> buildGeminiRequest(
            String transcript,
            ConversationSession session,
            String detectedLang,
            String role) {

        String langHint = "ur".equals(detectedLang)
                ? "\n[Detected user language: URDU — respond with language=\"ur\"]"
                : "\n[Detected user language: ENGLISH — respond with language=\"en\"]";

        String roleHint = buildRoleHint(role);

        return Map.of(
                "system_instruction", Map.of(
                        "parts", List.of(Map.of(
                                "text", SYSTEM_PROMPT + langHint + roleHint))
                ),
                "contents", session.buildContents(transcript),
                "generationConfig", Map.of(
                        "temperature", 0.05,
                        "maxOutputTokens", 350,
                        "responseMimeType", "application/json"
                )
        );
    }

    /**
     * Role-specific guidance appended to the system prompt.
     */
    private String buildRoleHint(String role) {
        if (role == null || role.isBlank()) {
            return """

                    ═══ USER ROLE ═══
                    CURRENT USER: ANONYMOUS (not logged in)
                    Allowed intents: NAVIGATE, HELP, GET_CROP_PRICE, GET_RECOMMENDATION,
                    LOGIN, VERIFY_OTP, GO_BACK, STOP_LISTENING, UNKNOWN.
                    For anything that needs authentication (bid, sell, wallet, notifications, etc.),
                    return the correct intent — the handler will prompt the user to log in.
                    """;
        }

        return switch (role.toUpperCase(Locale.ROOT)) {
            case "FARMER" -> """

                    ═══ USER ROLE ═══
                    CURRENT USER: FARMER (seller)
                    Preferred intents: GET_CROP_PRICE, CREATE_SELL_POST, ACCEPT_BID,
                    VIEW_WALLET, VIEW_PAYMENTS, CHECK_ORDER_STATUS,
                    GET_RECOMMENDATION, VIEW_NOTIFICATIONS.
                    Disallowed: PLACE_BID (farmers don't bid — they sell), VIEW_MY_BIDS.
                    If the user says "bid lagao", return PLACE_BID; the handler will
                    reject it with a friendly role error and suggest using sell instead.
                    """;
            case "BUYER" -> """

                    ═══ USER ROLE ═══
                    CURRENT USER: BUYER (purchaser)
                    Preferred intents: NAVIGATE to /marketplace, PLACE_BID, VIEW_MY_BIDS,
                    VIEW_WALLET, VIEW_PAYMENTS, CHECK_ORDER_STATUS, GET_CROP_PRICE,
                    COMPLETE_DEAL, CANCEL_DEAL.
                    Disallowed: CREATE_SELL_POST (farmers only), ACCEPT_BID (seller-only).
                    If the user says "bechna hai", return CREATE_SELL_POST; the handler
                    will reject it with a friendly role error.
                    """;
            case "ADMIN" -> """

                    ═══ USER ROLE ═══
                    CURRENT USER: ADMIN — full access.
                    """;
            default -> "";
        };
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

        // ⚡ STOP_LISTENING — HIGHEST PRIORITY (before everything else)
        if (matches(lower, "stop", "ruko", "bas", "band karo", "band kar",
                    "kaafi hai", "kaafi", "shukriya", "bye", "goodbye",
                    "exit", "quit", "enough", "that's it", "mic band",
                    "aur nahi", "sunna nahi", "bus karo")) {
            return new VoiceIntent("STOP_LISTENING", Map.of(), lang, 0.90);
        }

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

        // COMPLETE_DEAL — check first (specific phrases)
        if (matches(lower, "delivery ho gayi", "delivery done", "deal complete",
                    "paise release", "payment release", "soda mukammal",
                    "deal mukammal karo", "complete the deal")) {
            return new VoiceIntent("COMPLETE_DEAL", Map.of(), lang, 0.88);
        }

        // CANCEL_DEAL
        if (matches(lower, "cancel deal", "deal cancel", "soda cancel",
                    "soda rad", "deal rad", "deal wapas", "cancel the deal")) {
            return new VoiceIntent("CANCEL_DEAL", Map.of(), lang, 0.88);
        }

        // ACCEPT_BID
        if (matches(lower, "bid accept", "accept bid", "bid manzoor",
                    "accept kar", "bid qabool", "bid qabool karo")) {
            return new VoiceIntent("ACCEPT_BID", Map.of(), lang, 0.88);
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

        // VIEW_MY_BIDS (buyer-only — check before generic payment/history phrases)
        if (matches(lower, "meri bids", "my bids", "mere bids", "mera bid",
                    "my bid", "bids dikhao", "bid history", "bid list",
                    "kitni bids", "bids kahan")) {
            return new VoiceIntent("VIEW_MY_BIDS", Map.of(), lang, 0.88);
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