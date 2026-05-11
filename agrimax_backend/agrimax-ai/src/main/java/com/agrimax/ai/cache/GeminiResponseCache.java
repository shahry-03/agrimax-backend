package com.agrimax.ai.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple in-memory cache for Gemini intent detection responses.
 *
 * <p>Identical transcripts (same text) get the same cached VoiceIntent,
 * reducing Gemini API calls for repeated commands like "gehu ki price kya hai".</p>
 *
 * <ul>
 *   <li>Max entries: 500</li>
 *   <li>TTL: 10 minutes</li>
 *   <li>Cache key: lowercase-trimmed transcript</li>
 * </ul>
 */
@Slf4j
@Component
public class GeminiResponseCache {

    private static final int MAX_SIZE = 500;
    private static final long TTL_SECONDS = 600; // 10 minutes

    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public record CacheEntry(Object value, Instant expiresAt) {
        boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }

    /** Returns cached value if present and not expired; null otherwise. */
    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        CacheEntry entry = cache.get(normalize(key));
        if (entry == null || entry.isExpired()) {
            if (entry != null) cache.remove(normalize(key)); // evict expired
            return null;
        }
        log.debug("Cache HIT for key: \"{}\"", truncate(key));
        return (T) entry.value();
    }

    /** Stores a value in the cache with TTL. Evicts if cache is full. */
    public void put(String key, Object value) {
        if (cache.size() >= MAX_SIZE) {
            evictOldest();
        }
        cache.put(normalize(key), new CacheEntry(value, Instant.now().plusSeconds(TTL_SECONDS)));
        log.debug("Cache PUT for key: \"{}\"", truncate(key));
    }

    /** Scheduled cleanup every 5 minutes. */
    @Scheduled(fixedDelay = 300_000)
    public void evictExpired() {
        int before = cache.size();
        cache.entrySet().removeIf(e -> e.getValue().isExpired());
        int removed = before - cache.size();
        if (removed > 0) {
            log.debug("Gemini cache: evicted {} expired entries. Size: {}", removed, cache.size());
        }
    }

    public int size() {
        return cache.size();
    }

    // ── Helpers ──

    private String normalize(String key) {
        return key == null ? "" : key.trim().toLowerCase();
    }

    private String truncate(String key) {
        return key != null && key.length() > 50 ? key.substring(0, 50) + "..." : key;
    }

    private void evictOldest() {
        cache.entrySet().stream()
                .min((a, b) -> a.getValue().expiresAt().compareTo(b.getValue().expiresAt()))
                .ifPresent(e -> cache.remove(e.getKey()));
    }
}
