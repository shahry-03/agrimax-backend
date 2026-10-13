package com.agrimax.ai.dto.response;

/**
 * Result from a Whisper transcription.
 *
 * @param text        The transcribed text
 * @param language    ISO 639-1 language code detected by Whisper (e.g., "ur", "en")
 * @param durationSec Duration of audio in seconds (may be null if provider doesn't return)
 * @param provider    Which provider generated this result (groq, openai, local)
 */
public record TranscriptionResult(
        String text,
        String language,
        Double durationSec,
        String provider
) {
    public static TranscriptionResult of(String text, String language, String provider) {
        return new TranscriptionResult(text, language, null, provider);
    }
}