package com.agrimax.ai.dto.request;

/**
 * Text-to-Speech input.
 *
 * @param text     Text to synthesize (already in target language)
 * @param language "ur" (Urdu) or "en" (English)
 * @param voiceId  Optional specific voice (null = provider default for language)
 * @param speed    Speech rate: 0.5 (very slow) → 1.0 (normal) → 2.0 (fast)
 * @param gender   Optional "male" | "female" (null = provider default)
 */
public record TtsRequest(
        String text,
        String language,
        String voiceId,
        Double speed,
        String gender
) {
    /** Convenience — sensible defaults (normal speed, provider default voice). */
    public static TtsRequest of(String text, String language) {
        return new TtsRequest(text, language, null, 1.0, null);
    }

    public double effectiveSpeed() {
        return speed != null ? Math.max(0.5, Math.min(2.0, speed)) : 1.0;
    }

    public boolean isUrdu() {
        return "ur".equalsIgnoreCase(language);
    }
}