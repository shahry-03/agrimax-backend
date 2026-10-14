package com.agrimax.ai.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * ═══════════════════════════════════════════════════════════════════════
 * Advanced Language Detector — Production-Grade
 * ═══════════════════════════════════════════════════════════════════════
 *
 * Detects the language of farmer voice/text input across:
 *   - Urdu (script):         اردو (Devanagari-free RTL)
 *   - Roman Urdu:           "gehu kab bechni chahiye"
 *   - Hindi (Devanagari):   हिंदी (treated as Urdu for response)
 *   - English:              "when should I sell wheat"
 *   - Mixed:                "market kholo please"
 *
 * Detection layers (in priority order):
 *   1. Script-based (Unicode ranges) — HIGHEST confidence
 *   2. Weighted token-based scoring (Roman Urdu vs English)
 *   3. Bigram/trigram phrase matching
 *   4. Pattern-based suffix/prefix hints
 *   5. Statistical fallback
 *
 * Performance:
 *   - LRU cache (1000 entries) for repeated inputs
 *   - Thread-safe metrics counters
 *
 * ═══════════════════════════════════════════════════════════════════════
 */
@Slf4j
@Component
public class LanguageDetector {

    // ══════════════════════════════════════════════════════════════
    // SCRIPT DETECTION PATTERNS
    // ══════════════════════════════════════════════════════════════

    /** Urdu/Arabic script: \u0600–\u06FF (+ extended \u0750-\u077F) */
    private static final Pattern URDU_SCRIPT =
            Pattern.compile("[\\u0600-\\u06FF\\u0750-\\u077F]+");

    /** Devanagari script (Hindi): \u0900–\u097F */
    private static final Pattern DEVANAGARI_SCRIPT =
            Pattern.compile("[\\u0900-\\u097F]+");

    /** Arabic-Indic digits (۰۱۲۳) and Extended (٠١٢٣) */
    private static final Pattern ARABIC_DIGITS =
            Pattern.compile("[\\u0660-\\u0669\\u06F0-\\u06F9]+");

    // ══════════════════════════════════════════════════════════════
    // NOISE FILTERS (ignore these during detection)
    // ══════════════════════════════════════════════════════════════

    private static final Pattern URL_PATTERN =
            Pattern.compile("https?://\\S+|www\\.\\S+");

    private static final Pattern EMOJI_PATTERN =
            Pattern.compile("[\\x{1F300}-\\x{1F9FF}\\x{1F600}-\\x{1F64F}\\x{2600}-\\x{27BF}]+");

    private static final Pattern NUMERIC_NOISE =
            Pattern.compile("\\b\\d+\\b");

    // ══════════════════════════════════════════════════════════════
    // ROMAN URDU — WEIGHTED KEYWORDS
    // Higher weight = stronger signal for Roman Urdu
    // ══════════════════════════════════════════════════════════════

    /**
     * Weight = confidence boost when this token appears.
     * Scores: 3 = unique to Roman Urdu, 2 = strong, 1 = weak/ambiguous
     */
    private static final Map<String, Integer> ROMAN_URDU_WEIGHTS = buildRomanUrduWeights();

    private static Map<String, Integer> buildRomanUrduWeights() {
        Map<String, Integer> m = new HashMap<>();

        // ── Weight 3: Highly unique to Roman Urdu (very strong signal) ──
        List<String> w3 = List.of(
                // Interrogatives unique to Urdu/Hindi
                "kya", "kyun", "kyon", "kyunkay", "kabhi", "kaisi", "kaisa",
                "kitna", "kitni", "kitne", "kaise", "kaisay",
                // Urdu-specific verbs
                "bechna", "bechni", "bechoon", "bechdo",
                "kholo", "kholna", "dikhao", "dikhana", "batao", "batana",
                "karo", "karna", "karlo", "krlo",
                "chahiye", "chaiye", "chahta", "chahti",
                // Pronouns
                "mujhe", "mujhay", "mera", "meri", "mere",
                "tumhe", "tumhay", "tera", "teri", "tere",
                "humein", "humay", "hamara", "hamari",
                // Agriculture (Urdu/Hindi origin)
                "fasal", "keemat", "qeemat", "kimat", "bhav", "daam",
                "gehu", "gandum", "chawal", "makki", "kapas", "ganna",
                "sarson", "chana", "masoor", "moong", "urad", "bajra", "jowar",
                "khaad", "khad", "bimari", "keeray", "keera", "kida", "kide",
                "boai", "beej", "bonay", "lagana",
                "mausam", "barish", "dhoop", "garmi", "sardi", "hawa",
                // Common verbs unique
                "salah", "mashwara", "mashwara", "madad", "dua"
        );
        w3.forEach(k -> m.put(k, 3));

        // ── Weight 2: Strong Roman Urdu signal ──
        List<String> w2 = List.of(
                // Connectors/particles
                "hai", "hain", "tha", "thi", "the", "hoga", "hogi", "honge",
                "nahi", "nahin", "haan", "bilkul", "zaroor", "shayad",
                "aaj", "kal", "parson", "abhi", "phir", "phir",
                "yahan", "wahan", "kahan", "idhar", "udhar",
                // Actions
                "jao", "ja", "chalo", "chal", "aao", "aa",
                "de", "do", "lo", "le", "dena", "lena",
                "sakta", "sakti", "sakte",
                "lagna", "lagta", "lagti", "lagte",
                "milna", "milta", "milti", "milte",
                // Endings
                "raha", "rahi", "rahe", "raho",
                "gaya", "gayi", "gaye",
                "wala", "wali", "wale",
                // Words borrowed but common in Urdu speech
                "wallet", "batta", "paisa", "paisay", "paise",
                "bazaar", "dukan", "mandi", "market",
                "kisan", "kisaan", "fasal"
        );
        w2.forEach(k -> m.put(k, 2));

        // ── Weight 1: Weak/ambiguous (also English or common) ──
        List<String> w1 = List.of(
                "login", "daakhil", "lagan", "otp", "code",
                "phone", "number", "account",
                "theek", "theak", "acha", "accha", "bura",
                "bhai", "janab", "sahab", "sahib",
                "wapas", "wapasi", "jaldi", "aaram"
        );
        w1.forEach(k -> m.put(k, 1));

        return Collections.unmodifiableMap(m);
    }

    // ══════════════════════════════════════════════════════════════
    // ENGLISH — WEIGHTED KEYWORDS
    // ══════════════════════════════════════════════════════════════

    private static final Map<String, Integer> ENGLISH_WEIGHTS = buildEnglishWeights();

    private static Map<String, Integer> buildEnglishWeights() {
        Map<String, Integer> m = new HashMap<>();

        // Weight 3: Highly unique to English
        List<String> w3 = List.of(
                "the", "what", "when", "where", "why", "how", "which", "who",
                "should", "would", "could", "must", "shall",
                "please", "would", "hello", "thanks", "thank",
                "whenever", "wherever", "however", "therefore"
        );
        w3.forEach(k -> m.put(k, 3));

        // Weight 2: Strong English signal
        List<String> w2 = List.of(
                "is", "am", "are", "was", "were", "be", "been", "being",
                "have", "has", "had", "do", "does", "did",
                "can", "may", "might", "will",
                "this", "that", "these", "those",
                "i", "me", "my", "we", "our", "you", "your",
                "he", "him", "his", "she", "her",
                "it", "its", "they", "them", "their",
                "want", "need", "give", "take", "get",
                "make", "show", "tell", "buy", "sell", "check",
                "of", "to", "for", "with", "about", "into", "at", "on", "in"
        );
        w2.forEach(k -> m.put(k, 2));

        // Weight 1: Ambiguous (also common in Urdu code-switching)
        List<String> w1 = List.of(
                "price", "market", "wheat", "rice", "cotton", "corn",
                "crop", "farmer", "login", "wallet", "balance",
                "phone", "number", "code", "account"
        );
        w1.forEach(k -> m.put(k, 1));

        return Collections.unmodifiableMap(m);
    }

    // ══════════════════════════════════════════════════════════════
    // BIGRAM / TRIGRAM PHRASES (multi-word signals)
    // ══════════════════════════════════════════════════════════════

    private static final Set<String> ROMAN_URDU_BIGRAMS = Set.of(
            "kab bechni", "kab bechoon", "kab lagana", "kab bonay",
            "kya hai", "kya hoga", "kya karo", "kya karoon",
            "meri fasal", "mera wallet", "mera paisa",
            "gehu ki", "chawal ki", "fasal ki",
            "keemat kya", "rate kya", "bhav kya",
            "kaise theek", "kaisay theek",
            "mujhe login", "mujhe bechna", "mujhe chahiye",
            "kahan hai", "kitna hai", "kitni hai"
    );

    private static final Set<String> ENGLISH_BIGRAMS = Set.of(
            "should i", "can i", "how do", "what is", "where is", "when should",
            "i want", "i need", "i have", "please tell", "please show",
            "the price", "the market", "the crop", "the wallet",
            "my wallet", "my account", "my crop"
    );

    // ══════════════════════════════════════════════════════════════
    // ROMAN URDU PATTERNS
    // ══════════════════════════════════════════════════════════════

    private static final Pattern[] ROMAN_URDU_PATTERNS = {
            Pattern.compile("\\b(hai|hain|tha|thi|hoga|hogi)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(karo|karna|kron|karlay)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(mera|meri|mere|mujhe|tumhe)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(kya|kab|kyun|kaise|kitna|kaisi)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(bech\\w*|khol\\w*|dikha\\w*|bata\\w*)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(raha|rahi|rahe|gaya|gayi|wala|wali)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(hai|karo|kya|mera|meri|bechni|hoga)\\s*[?؟]\\s*$",
                    Pattern.CASE_INSENSITIVE)
    };

    // ══════════════════════════════════════════════════════════════
    // CACHE (LRU-style, bounded)
    // ══════════════════════════════════════════════════════════════

    private static final int CACHE_MAX_SIZE = 1000;
    private final Map<String, DetectionResult> cache = new ConcurrentHashMap<>();

    // ══════════════════════════════════════════════════════════════
    // METRICS (observability)
    // ══════════════════════════════════════════════════════════════

    private final AtomicLong totalDetections = new AtomicLong();
    private final AtomicLong urduScriptCount = new AtomicLong();
    private final AtomicLong romanUrduCount = new AtomicLong();
    private final AtomicLong englishCount = new AtomicLong();
    private final AtomicLong hindiAsUrduCount = new AtomicLong();
    private final AtomicLong mixedCount = new AtomicLong();
    private final AtomicLong cacheHits = new AtomicLong();

    // ══════════════════════════════════════════════════════════════
    // PUBLIC API
    // ══════════════════════════════════════════════════════════════

    /**
     * Detect the language of the given transcript.
     * Thread-safe, cached, and metrics-instrumented.
     */
    public DetectionResult detect(String transcript) {
        if (transcript == null || transcript.isBlank()) {
            return new DetectionResult("ur", 0.30, "empty-default-urdu");
        }

        // Normalize input for cache key
        String cacheKey = transcript.trim().toLowerCase();
        if (cacheKey.length() > 500) {
            cacheKey = cacheKey.substring(0, 500);
        }

        // Cache check
        DetectionResult cached = cache.get(cacheKey);
        if (cached != null) {
            cacheHits.incrementAndGet();
            log.debug("Language cache HIT: {} ({})", cached.language(), cached.reason());
            return cached;
        }

        // Preprocess: remove noise
        String clean = preprocess(transcript);
        if (clean.isBlank()) {
            return new DetectionResult("ur", 0.30, "noise-only-default-urdu");
        }

        totalDetections.incrementAndGet();

        // ── Layer 1: Script detection (highest confidence) ──
        DetectionResult result = detectFromScript(clean);

        // ── Layer 2: Weighted token scoring ──
        if (result == null) {
            result = detectFromWeightedTokens(clean);
        }

        // ── Layer 3: Bigram phrases ──
        if (result == null) {
            result = detectFromBigrams(clean);
        }

        // ── Layer 4: Pattern-based hints ──
        if (result == null) {
            result = detectFromPatterns(clean);
        }

        // ── Layer 5: Fallback ──
        if (result == null) {
            result = new DetectionResult("en", 0.50, "fallback-english");
        }

        // Update metrics
        updateMetrics(result);

        // Store in cache (bounded)
        if (cache.size() < CACHE_MAX_SIZE) {
            cache.put(cacheKey, result);
        }

        log.info("🌐 Language: {} | confidence: {} | reason: {} | input: \"{}\"",
                result.language(), result.confidence(), result.reason(), truncate(transcript));

        return result;
    }

    /**
     * Get detector health/metrics for monitoring.
     */
    public Map<String, Object> getMetrics() {
        long total = totalDetections.get();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("totalDetections", total);
        m.put("cacheHits", cacheHits.get());
        m.put("cacheSize", cache.size());
        m.put("urduScript", urduScriptCount.get());
        m.put("hindiAsUrdu", hindiAsUrduCount.get());
        m.put("romanUrdu", romanUrduCount.get());
        m.put("english", englishCount.get());
        m.put("mixed", mixedCount.get());

        if (total > 0) {
            m.put("urduScriptPct", 100.0 * urduScriptCount.get() / total);
            m.put("romanUrduPct", 100.0 * romanUrduCount.get() / total);
            m.put("englishPct", 100.0 * englishCount.get() / total);
        }
        return m;
    }

    // ══════════════════════════════════════════════════════════════
    // LAYER 1: SCRIPT DETECTION
    // ══════════════════════════════════════════════════════════════

    private DetectionResult detectFromScript(String text) {
        int urduChars = countChars(URDU_SCRIPT, text);
        int hindiChars = countChars(DEVANAGARI_SCRIPT, text);
        int arabicDigitChars = countChars(ARABIC_DIGITS, text);

        // Effective length (exclude whitespace and digits)
        String noSpace = text.replaceAll("\\s+", "");
        int totalChars = noSpace.length() - arabicDigitChars;
        if (totalChars <= 0) return null;

        double urduRatio = (double) urduChars / totalChars;
        double hindiRatio = (double) hindiChars / totalChars;

        // Strong Urdu script signal (>50% Urdu chars)
        if (urduRatio > 0.5) {
            double conf = Math.min(0.99, 0.85 + urduRatio * 0.14);
            return new DetectionResult("ur", conf, "urdu-script");
        }

        // Devanagari (user spoke Hindi/Urdu — respond in Urdu)
        if (hindiRatio > 0.5) {
            double conf = Math.min(0.90, 0.75 + hindiRatio * 0.15);
            return new DetectionResult("ur", conf, "hindi-script-as-urdu");
        }

        // Mixed script (Urdu + Latin) → lean Urdu
        if (urduRatio > 0.2 && urduRatio < 0.5) {
            return new DetectionResult("ur", 0.75, "mixed-script-urdu-dominant");
        }

        return null;
    }

    // ══════════════════════════════════════════════════════════════
    // LAYER 2: WEIGHTED TOKEN SCORING
    // ══════════════════════════════════════════════════════════════

    private DetectionResult detectFromWeightedTokens(String text) {
        String[] tokens = text.toLowerCase().split("[^a-z]+");
        if (tokens.length == 0) return null;

        int urduScore = 0;
        int englishScore = 0;
        int meaningfulTokens = 0;

        for (String token : tokens) {
            if (token.isBlank() || token.length() < 2) continue;
            meaningfulTokens++;

            Integer uw = ROMAN_URDU_WEIGHTS.get(token);
            if (uw != null) urduScore += uw;

            Integer ew = ENGLISH_WEIGHTS.get(token);
            if (ew != null) englishScore += ew;
        }

        if (meaningfulTokens == 0) return null;

        int totalScore = urduScore + englishScore;
        if (totalScore == 0) return null;

        double urduRatio = (double) urduScore / totalScore;
        double dominance = (double) Math.abs(urduScore - englishScore) / totalScore;

        // Clear Urdu winner
        if (urduScore >= 3 && urduRatio >= 0.60) {
            double conf = calibrateConfidence(urduRatio, meaningfulTokens, dominance);
            return new DetectionResult("ur", conf, "roman-urdu-tokens");
        }

        // Clear English winner
        if (englishScore >= 3 && urduRatio <= 0.40) {
            double conf = calibrateConfidence(1 - urduRatio, meaningfulTokens, dominance);
            return new DetectionResult("en", conf, "english-tokens");
        }

        // Mixed: pick dominant
        if (totalScore >= 5 && dominance >= 0.30) {
            String lang = urduRatio > 0.5 ? "ur" : "en";
            return new DetectionResult(lang, 0.65, "mixed-dominant-" + lang);
        }

        return null;
    }

    // ══════════════════════════════════════════════════════════════
    // LAYER 3: BIGRAM PHRASES
    // ══════════════════════════════════════════════════════════════

    private DetectionResult detectFromBigrams(String text) {
        String lower = text.toLowerCase();

        int urduHits = 0;
        for (String bg : ROMAN_URDU_BIGRAMS) {
            if (lower.contains(bg)) urduHits++;
        }

        int engHits = 0;
        for (String bg : ENGLISH_BIGRAMS) {
            if (lower.contains(bg)) engHits++;
        }

        if (urduHits > engHits && urduHits >= 1) {
            return new DetectionResult("ur", 0.80, "roman-urdu-bigrams");
        }
        if (engHits > urduHits && engHits >= 1) {
            return new DetectionResult("en", 0.80, "english-bigrams");
        }

        return null;
    }

    // ══════════════════════════════════════════════════════════════
    // LAYER 4: PATTERN-BASED
    // ══════════════════════════════════════════════════════════════

    private DetectionResult detectFromPatterns(String text) {
        int matches = 0;
        for (Pattern p : ROMAN_URDU_PATTERNS) {
            if (p.matcher(text).find()) matches++;
        }

        if (matches >= 2) {
            return new DetectionResult("ur", 0.80, "roman-urdu-patterns-strong");
        }
        if (matches == 1) {
            return new DetectionResult("ur", 0.70, "roman-urdu-patterns");
        }
        return null;
    }

    // ══════════════════════════════════════════════════════════════
    // HELPERS
    // ══════════════════════════════════════════════════════════════

    /**
     * Preprocess: strip URLs, emojis, replace numbers with spaces.
     * Keeps detection focused on actual words.
     */
    private String preprocess(String text) {
        String s = URL_PATTERN.matcher(text).replaceAll(" ");
        s = EMOJI_PATTERN.matcher(s).replaceAll(" ");
        s = NUMERIC_NOISE.matcher(s).replaceAll(" ");
        s = s.replaceAll("\\s+", " ").trim();
        return s;
    }

    /**
     * Calibrate confidence based on:
     *   - ratio: how one-sided the score is (0.5 → 1.0)
     *   - tokenCount: more tokens = more confidence
     *   - dominance: how far apart the two scores are
     */
    private double calibrateConfidence(double ratio, int tokenCount, double dominance) {
        // Base from ratio (0.6 → 0.55, 1.0 → 0.85)
        double base = 0.40 + ratio * 0.45;

        // Token count bonus (max 0.08)
        double tokenBonus = Math.min(0.08, tokenCount * 0.01);

        // Dominance bonus (max 0.07)
        double domBonus = dominance * 0.07;

        return Math.min(0.95, base + tokenBonus + domBonus);
    }

    private int countChars(Pattern p, String text) {
        Matcher m = p.matcher(text);
        int count = 0;
        while (m.find()) {
            count += m.group().length();
        }
        return count;
    }

    private void updateMetrics(DetectionResult r) {
        switch (r.reason()) {
            case "urdu-script" -> urduScriptCount.incrementAndGet();
            case "hindi-script-as-urdu" -> hindiAsUrduCount.incrementAndGet();
            case "roman-urdu-tokens",
                 "roman-urdu-bigrams",
                 "roman-urdu-patterns",
                 "roman-urdu-patterns-strong",
                 "mixed-script-urdu-dominant" -> romanUrduCount.incrementAndGet();
            case "english-tokens", "english-bigrams" -> englishCount.incrementAndGet();
            case "mixed-dominant-ur", "mixed-dominant-en" -> mixedCount.incrementAndGet();
        }
    }

    private String truncate(String s) {
        if (s == null) return "";
        return s.length() > 60 ? s.substring(0, 60) + "..." : s;
    }

    // ══════════════════════════════════════════════════════════════
    // RESULT RECORD
    // ══════════════════════════════════════════════════════════════

    /**
     * @param language   ISO 639-1: "ur" (Urdu) or "en" (English)
     * @param confidence 0.0 to 1.0
     * @param reason     Detection method (for logging/observability)
     */
    public record DetectionResult(String language, double confidence, String reason) {
        public boolean isUrdu()    { return "ur".equals(language); }
        public boolean isEnglish() { return "en".equals(language); }
        public boolean isHighConfidence() { return confidence >= 0.75; }
    }
}