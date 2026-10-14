package com.agrimax.ai.llm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Orchestrates multiple LLM providers with:
 * - Priority-based selection (Gemini → Groq)
 * - Automatic failover on error
 * - Circuit breaker with cooldown for rate-limited providers
 * - Exponential backoff escalation
 *
 * <p>If ALL providers fail, throws LlmException so callers can use templates.</p>
 */
@Slf4j
@Service
public class HybridLlmService {

    private final LlmProviderFactory providerFactory;

    // Circuit breaker state per provider
    private final Map<String, Instant> cooldownUntil = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> consecutiveFailures = new ConcurrentHashMap<>();

    // Cooldown durations
    private static final Duration RATE_LIMIT_COOLDOWN = Duration.ofMinutes(5);
    private static final Duration ERROR_COOLDOWN      = Duration.ofSeconds(30);
    private static final Duration MAX_COOLDOWN        = Duration.ofMinutes(30);

    public HybridLlmService(LlmProviderFactory providerFactory) {
        this.providerFactory = providerFactory;
    }

    /**
     * Send a chat request to the best available provider.
     * Tries providers in priority order, skipping those in cooldown.
     *
     * @throws LlmException if all providers fail
     */
    public String chat(String systemPrompt, String userMessage, double temperature, int maxTokens) {
        List<LlmProvider> providers = providerFactory.getProviders();

        if (providers.isEmpty()) {
            throw new LlmException("hybrid", "No LLM providers configured");
        }

        LlmException lastError = null;
        int triedCount = 0;

        for (LlmProvider provider : providers) {
            String name = provider.getName();

            // Circuit breaker: skip providers in cooldown
            if (isInCooldown(name)) {
                log.debug("[{}] Skipping — in cooldown until {}", name, cooldownUntil.get(name));
                continue;
            }

            triedCount++;
            try {
                long start = System.currentTimeMillis();
                String response = provider.chat(systemPrompt, userMessage, temperature, maxTokens);
                long elapsed = System.currentTimeMillis() - start;

                // Success — reset failure counter, clear cooldown
                recordSuccess(name);
                log.info("✅ [{}] Responded in {}ms", name, elapsed);
                return response;

            } catch (LlmException e) {
                lastError = e;
                recordFailure(name, e);
                log.warn("[{}] Failed ({}), trying next provider...", name, e.getMessage());
                // Continue to next provider
            }
        }

        // All providers failed
        String errorMsg = triedCount == 0
                ? "All LLM providers are in cooldown"
                : "All LLM providers failed (tried " + triedCount + ")";

        log.error("❌ {} — Last error: {}", errorMsg,
                lastError != null ? lastError.getMessage() : "unknown");

        throw new LlmException("hybrid", errorMsg, lastError);
    }

    // ══════════════════════════════════════════════════════════
    // CIRCUIT BREAKER LOGIC
    // ══════════════════════════════════════════════════════════

    private boolean isInCooldown(String provider) {
        Instant until = cooldownUntil.get(provider);
        if (until == null) return false;
        if (Instant.now().isAfter(until)) {
            cooldownUntil.remove(provider);
            return false;
        }
        return true;
    }

    private void recordSuccess(String provider) {
        consecutiveFailures.remove(provider);
        cooldownUntil.remove(provider);
    }

    private void recordFailure(String provider, LlmException e) {
        int failures = consecutiveFailures
                .computeIfAbsent(provider, k -> new AtomicInteger(0))
                .incrementAndGet();

        Duration cooldown;

        if (e.isRateLimited()) {
            // Exponential backoff for rate limits: 5min × failures (capped at 30min)
            long minutes = Math.min(5L * failures, MAX_COOLDOWN.toMinutes());
            cooldown = Duration.ofMinutes(minutes);
            log.warn("🚫 [{}] Rate-limited. Cooldown: {} min (failure #{})", provider, minutes, failures);
        } else {
            cooldown = ERROR_COOLDOWN;
            log.warn("⚠️  [{}] Error. Cooldown: {}s (failure #{})", provider, ERROR_COOLDOWN.toSeconds(), failures);
        }

        cooldownUntil.put(provider, Instant.now().plus(cooldown));
    }

    // ══════════════════════════════════════════════════════════
    // OBSERVABILITY
    // ══════════════════════════════════════════════════════════

    public Map<String, Object> getStatus() {
        Map<String, Object> status = new ConcurrentHashMap<>();
        providerFactory.getProviders().forEach(p -> {
            Map<String, Object> info = new ConcurrentHashMap<>();
            info.put("priority", p.getPriority());
            info.put("inCooldown", isInCooldown(p.getName()));
            info.put("cooldownUntil", cooldownUntil.get(p.getName()));
            info.put("consecutiveFailures",
                    consecutiveFailures.getOrDefault(p.getName(), new AtomicInteger(0)).get());
            status.put(p.getName(), info);
        });
        return status;
    }
}