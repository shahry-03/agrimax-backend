package com.agrimax.ai.service.whisper;

import com.agrimax.ai.exception.WhisperException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Selects the best available Whisper provider based on priority.
 * Supports fallback chain: tries providers in order until one succeeds.
 */
@Slf4j
@Component
public class WhisperProviderFactory {

    private final List<WhisperProvider> availableProviders;

    public WhisperProviderFactory(List<WhisperProvider> allProviders) {
        this.availableProviders = allProviders.stream()
                .filter(WhisperProvider::isAvailable)
                .sorted(Comparator.comparingInt(WhisperProvider::getPriority))
                .collect(Collectors.toList());

        if (availableProviders.isEmpty()) {
            log.warn("⚠️ No Whisper providers configured. Audio transcription will be unavailable.");
        } else {
            log.info("✅ Whisper providers registered (in priority order): {}",
                    availableProviders.stream().map(WhisperProvider::getName).toList());
        }
    }

    /**
     * @return Ordered list of available providers (used for fallback chain)
     */
    public List<WhisperProvider> getProviderChain() {
        if (availableProviders.isEmpty()) {
            throw new WhisperException("No Whisper providers available");
        }
        return availableProviders;
    }

    public boolean hasProviders() {
        return !availableProviders.isEmpty();
    }
}