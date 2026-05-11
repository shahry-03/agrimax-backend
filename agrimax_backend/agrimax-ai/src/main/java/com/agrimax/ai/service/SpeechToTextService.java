package com.agrimax.ai.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Mock Speech-to-Text service.
 * In production, integrate with a real STT provider (Google Cloud Speech, Whisper, etc.)
 * For now, accepts text transcripts directly via the API.
 */
@Slf4j
@Service
public class SpeechToTextService {

    /**
     * Mock: simply returns the provided text as the "transcript".
     * In production, this would accept audio bytes and call an STT API.
     */
    public String transcribe(String textOrAudioRef) {
        log.info("Mock STT — using text directly: {}", textOrAudioRef);
        return textOrAudioRef;
    }
}
