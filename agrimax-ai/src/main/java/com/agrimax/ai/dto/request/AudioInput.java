package com.agrimax.ai.dto.request;

/**
 * Input audio for transcription.
 *
 * @param audioBytes      Raw audio bytes
 * @param filename        Original filename (helps providers infer format)
 * @param contentType     MIME type (e.g., "audio/webm", "audio/wav")
 * @param languageHint    Optional language hint ("ur", "en") — null for auto-detect
 */
public record AudioInput(
        byte[] audioBytes,
        String filename,
        String contentType,
        String languageHint
) {
    public boolean hasLanguageHint() {
        return languageHint != null && !languageHint.isBlank();
    }
}