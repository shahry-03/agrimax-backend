package com.agrimax.ai.tts;

import com.agrimax.ai.dto.response.TtsResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory audio cache.
 *
 * <p>CRITICAL for voice-first UX — common phrases like "اپنا فون نمبر بتائیں"
 * will be requested thousands of times. Cached hits = instant response.</p>
 *
 * <ul>
 *   <li>Max entries: 500</li>
 *   <li>Max total bytes: 50 MB (~3 sec audio avg × 500 entries)</li>
 *   <li>TTL: 1 hour</li>
 * </ul>
 */
@Slf4j
@Component
public class TtsCache {

    private static final int MAX_ENTRIES = 500;
    private static final long MAX_TOTAL_BYTES = 50L * 1024 * 1024; // 50 MB
    private static final long TTL_SECONDS = 3600; // 1 hour

    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final AtomicLong totalBytes = new AtomicLong(0);

    public record CacheEntry(TtsResult result, Instant expiresAt) {
        boolean isExpired() { return Instant.now().isAfter(expiresAt); }
    }

    public TtsResult get(String key) {
        CacheEntry entry = cache.get(key);
        if (entry == null) return null;
        if (entry.isExpired()) {
            removeEntry(key);
            return null;
        }
        log.debug("TTS cache HIT: {}", key.substring(0, Math.min(16, key.length())));
        return entry.result();
    }

    public void put(String key, TtsResult result) {
        // Evict if needed
        while (cache.size() >= MAX_ENTRIES || totalBytes.get() >= MAX_TOTAL_BYTES) {
            if (!evictOldest()) break;
        }
        CacheEntry entry = new CacheEntry(result, Instant.now().plusSeconds(TTL_SECONDS));
        CacheEntry old = cache.put(key, entry);
        if (old != null) totalBytes.addAndGet(-old.result().size());
        totalBytes.addAndGet(result.size());
    }

    @Scheduled(fixedDelay = 600_000) // 10 min
    public void evictExpired() {
        int before = cache.size();
        long bytesBefore = totalBytes.get();
        cache.entrySet().removeIf(e -> {
            if (e.getValue().isExpired()) {
                totalBytes.addAndGet(-e.getValue().result().size());
                return true;
            }
            return false;
        });
        int removed = before - cache.size();
        if (removed > 0) {
            log.info("TTS cache: evicted {} expired entries, freed {} KB. Size: {}/{} entries, {}/{} MB",
                    removed, (bytesBefore - totalBytes.get()) / 1024,
                    cache.size(), MAX_ENTRIES,
                    totalBytes.get() / (1024 * 1024), MAX_TOTAL_BYTES / (1024 * 1024));
        }
    }

    private void removeEntry(String key) {
        CacheEntry removed = cache.remove(key);
        if (removed != null) totalBytes.addAndGet(-removed.result().size());
    }

    private boolean evictOldest() {
        return cache.entrySet().stream()
                .min((a, b) -> a.getValue().expiresAt().compareTo(b.getValue().expiresAt()))
                .map(e -> {
                    removeEntry(e.getKey());
                    return true;
                })
                .orElse(false);
    }

    public int size() { return cache.size(); }
    public long totalBytes() { return totalBytes.get(); }
}