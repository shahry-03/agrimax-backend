package com.agrimax.ai.tts;

import com.agrimax.ai.exception.TtsException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
public class TtsProviderFactory {

    private final List<TtsProvider> providers;

    public TtsProviderFactory(List<TtsProvider> allProviders) {
        this.providers = allProviders.stream()
                .filter(TtsProvider::isAvailable)
                .sorted(Comparator.comparingInt(TtsProvider::getPriority))
                .collect(Collectors.toList());

        if (providers.isEmpty()) {
            log.warn("⚠️  No TTS providers configured. Voice replies will fallback to text-only.");
        } else {
            log.info("✅ TTS providers registered (priority order): {}",
                    providers.stream().map(TtsProvider::getName).toList());
        }
    }

    public List<TtsProvider> getProviderChain() {
        if (providers.isEmpty()) {
            throw new TtsException("No TTS providers available");
        }
        return providers;
    }

    public boolean hasProviders() {
        return !providers.isEmpty();
    }
}