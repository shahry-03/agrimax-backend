package com.agrimax.common.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class RateLimitServiceImpl implements RateLimitService {

    /** bucketName -> (clientKey -> Bucket) */
    private final Map<String, Map<String, Bucket>> buckets = new ConcurrentHashMap<>();

    /** bucketName+":"+clientKey -> last access time (for cleanup) */
    private final Map<String, Instant> lastAccess = new ConcurrentHashMap<>();

    @Override
    public boolean tryConsume(String bucketName, String clientKey, RateLimit config) {
        Bucket bucket = getOrCreateBucket(bucketName, clientKey, config);
        lastAccess.put(bucketName + ":" + clientKey, Instant.now());

        boolean allowed = bucket.tryConsume(1);
        if (!allowed) {
            log.warn("Rate limit exceeded: bucket={} client={}", bucketName, clientKey);
        }
        return allowed;
    }

    @Override
    public long getRemainingTokens(String bucketName, String clientKey, RateLimit config) {
        return getOrCreateBucket(bucketName, clientKey, config).getAvailableTokens();
    }

    @Override
    public void reset(String bucketName, String clientKey) {
        Map<String, Bucket> m = buckets.get(bucketName);
        if (m != null) m.remove(clientKey);
        lastAccess.remove(bucketName + ":" + clientKey);
    }

    @Override
    public void resetAll() {
        buckets.clear();
        lastAccess.clear();
    }

    /**
     * Cleanup idle buckets after 2 hours of inactivity.
     * Runs every 15 minutes.
     */
    @Scheduled(fixedRate = 900_000)
    public void cleanup() {
        Instant cutoff = Instant.now().minus(Duration.ofHours(2));
        int removed = 0;

        for (var bucketEntry : buckets.entrySet()) {
            String bucketName = bucketEntry.getKey();
            Map<String, Bucket> clientBuckets = bucketEntry.getValue();

            var iterator = clientBuckets.keySet().iterator();
            while (iterator.hasNext()) {
                String clientKey = iterator.next();
                Instant last = lastAccess.get(bucketName + ":" + clientKey);
                if (last == null || last.isBefore(cutoff)) {
                    iterator.remove();
                    lastAccess.remove(bucketName + ":" + clientKey);
                    removed++;
                }
            }
            if (clientBuckets.isEmpty()) buckets.remove(bucketName);
        }

        if (removed > 0) {
            log.debug("Rate limit cleanup: removed {} idle buckets, active={}", removed, buckets.size());
        }
    }

    // ─────────────────────────────────────────────────────────

    private Bucket getOrCreateBucket(String bucketName, String clientKey, RateLimit config) {
        return buckets
                .computeIfAbsent(bucketName, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(clientKey, k -> createBucket(config));
    }

    private Bucket createBucket(RateLimit config) {
        long refillTokens = config.refillTokens() > 0 ? config.refillTokens() : config.capacity();
        Duration refillDuration = toDuration(config.refillPeriod(), config.refillUnit());

        Bandwidth limit = Bandwidth.builder()
                .capacity(config.capacity())
                .refillGreedy(refillTokens, refillDuration)
                .build();

        return Bucket.builder().addLimit(limit).build();
    }

    private Duration toDuration(long period, RateLimit.RefillUnit unit) {
        return switch (unit) {
            case SECONDS -> Duration.ofSeconds(period);
            case MINUTES -> Duration.ofMinutes(period);
            case HOURS   -> Duration.ofHours(period);
        };
    }
}