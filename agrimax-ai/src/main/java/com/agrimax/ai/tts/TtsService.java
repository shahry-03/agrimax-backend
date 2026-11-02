package com.agrimax.ai.tts;

import com.agrimax.ai.dto.request.TtsRequest;
import com.agrimax.ai.dto.response.TtsResult;
import com.agrimax.ai.exception.TtsException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Orchestrates TTS: cache → provider chain → cache.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TtsService {

    private final TtsProviderFactory providerFactory;
    private final TtsCache cache;

    public TtsResult synthesize(TtsRequest request) {
        if (request.text() == null || request.text().isBlank()) {
            throw new TtsException("Empty text");
        }

        // 1. Cache check
        String cacheKey = buildCacheKey(request);
        TtsResult cached = cache.get(cacheKey);
        if (cached != null) return cached;

        // 2. Provider chain
        if (!providerFactory.hasProviders()) {
            throw new TtsException("No TTS providers available");
        }

        TtsException lastError = null;
        for (TtsProvider provider : providerFactory.getProviderChain()) {
            if (!provider.supportsLanguage(request.language())) {
                log.debug("[{}] does not support language '{}'", provider.getName(), request.language());
                continue;
            }
            try {
                TtsResult result = provider.synthesize(request);
                if (result.audioBytes() != null && result.audioBytes().length > 0) {
                    cache.put(cacheKey, result);
                    return result;
                }
            } catch (TtsException e) {
                log.warn("[{}] TTS failed: {}", provider.getName(), e.getMessage());
                lastError = e;
            }
        }

        throw new TtsException("All TTS providers failed", lastError);
    }

    /** Cache key: SHA-256 of (text + language + voice + speed). */
    private String buildCacheKey(TtsRequest r) {
        String raw = r.text() + "|" + r.language() + "|"
                + (r.voiceId() != null ? r.voiceId() : "default") + "|"
                + (r.gender() != null ? r.gender() : "default") + "|"
                + r.effectiveSpeed();
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(raw.hashCode()); // Fallback
        }
    }

    public TtsCache getCache() { return cache; }
}