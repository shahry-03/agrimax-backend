package com.agrimax.ai.dto.response;

/**
 * Result from a TTS synthesis.
 *
 * @param audioBytes   Raw audio (typically MP3)
 * @param contentType  MIME type (e.g., "audio/mpeg")
 * @param durationMs   Duration in milliseconds (null if provider doesn't return)
 * @param provider     Which provider generated this (azure, google, etc.)
 * @param voiceId      The actual voice used (e.g., "ur-PK-UzmaNeural")
 */
public record TtsResult(
        byte[] audioBytes,
        String contentType,
        Long durationMs,
        String provider,
        String voiceId
) {
    public int size() {
        return audioBytes != null ? audioBytes.length : 0;
    }
}