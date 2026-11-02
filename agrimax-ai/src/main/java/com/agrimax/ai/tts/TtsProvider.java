package com.agrimax.ai.tts;

import com.agrimax.ai.dto.request.TtsRequest;
import com.agrimax.ai.dto.response.TtsResult;

/**
 * Strategy interface for Text-to-Speech providers.
 * Each implementation (Azure, Google, etc.) implements this contract.
 */
public interface TtsProvider {

    /** Unique provider name (e.g., "azure", "google") */
    String getName();

    /** Lower value = tried first (like WhisperProvider) */
    default int getPriority() { return 100; }

    /** true if this provider is properly configured (API key present + enabled) */
    boolean isAvailable();

    /**
     * Language support check — CRITICAL for TTS.
     * Not every provider supports Urdu. Router will skip non-supporting providers.
     */
    boolean supportsLanguage(String language);

    /**
     * Synthesize text to audio.
     *
     * @throws TtsException on failure
     */
    TtsResult synthesize(TtsRequest request);
}