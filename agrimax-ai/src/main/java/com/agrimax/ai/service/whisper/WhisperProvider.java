package com.agrimax.ai.service.whisper;

import com.agrimax.ai.dto.request.AudioInput;
import com.agrimax.ai.dto.response.TranscriptionResult;

/**
 * Strategy interface for Speech-to-Text providers.
 * Each implementation (Groq, OpenAI, Local) implements this contract.
 */
public interface WhisperProvider {

    /**
     * @return Unique provider name (e.g., "groq", "openai")
     */
    String getName();

    /**
     * @return Priority — lower numbers tried first
     */
    default int getPriority() {
        return 100;
    }

    /**
     * @return true if this provider is properly configured (API key present)
     */
    boolean isAvailable();

    /**
     * Transcribe audio to text.
     *
     * @param input Audio input (bytes + metadata)
     * @return Transcription result with detected language
     * @throws WhisperException on failure
     */
    TranscriptionResult transcribe(AudioInput input);
}