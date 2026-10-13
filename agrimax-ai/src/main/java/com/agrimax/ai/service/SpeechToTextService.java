package com.agrimax.ai.service;

import com.agrimax.ai.exception.WhisperException;
import com.agrimax.ai.service.whisper.WhisperProvider;
import com.agrimax.ai.service.whisper.WhisperProviderFactory;
import com.agrimax.ai.dto.request.AudioInput;
import com.agrimax.ai.dto.response.TranscriptionResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Speech-to-Text service with multi-provider fallback chain.
 *
 * <p>Behavior:</p>
 * <ul>
 *   <li>If text is provided (mock mode), returns it as-is.</li>
 *   <li>If audio bytes are provided, tries providers in priority order (Groq → OpenAI).</li>
 *   <li>Auto-detects language unless a hint is provided.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SpeechToTextService {

    private final WhisperProviderFactory providerFactory;

    /**
     * Mock-mode: text passes through unchanged.
     * Kept for backward compatibility with existing /voice/command endpoint.
     */
    public String transcribe(String textOrAudioRef) {
        log.debug("Mock STT — passing text through: {}", textOrAudioRef);
        return textOrAudioRef;
    }

    /**
     * Real transcription from audio bytes.
     * Tries each provider in priority order until one succeeds.
     *
     * @param input Audio + optional language hint
     * @return Transcription with detected language
     * @throws WhisperException if all providers fail
     */
    public TranscriptionResult transcribeAudio(AudioInput input) {
        if (input.audioBytes() == null || input.audioBytes().length == 0) {
            throw new WhisperException("Audio bytes are empty");
        }

        log.info("Transcribing {} bytes of {} (langHint={})",
                input.audioBytes().length,
                input.contentType() != null ? input.contentType() : "unknown",
                input.hasLanguageHint() ? input.languageHint() : "auto");

        WhisperException lastError = null;

        for (WhisperProvider provider : providerFactory.getProviderChain()) {
            try {
                TranscriptionResult result = provider.transcribe(input);

                if (result.text() == null || result.text().isBlank()) {
                    log.warn("[{}] Returned empty transcription, trying next provider", provider.getName());
                    continue;
                }

                log.info("✅ Transcribed via {} | lang={} | text=\"{}\"",
                        provider.getName(), result.language(), result.text());
                return result;

            } catch (WhisperException e) {
                log.warn("[{}] Failed: {}", provider.getName(), e.getMessage());
                lastError = e;
                // Continue to next provider
            }
        }

        throw new WhisperException(
                "All Whisper providers failed. Last error: " +
                (lastError != null ? lastError.getMessage() : "unknown"),
                lastError
        );
    }
}