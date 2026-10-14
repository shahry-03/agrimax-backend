package com.agrimax.ai.llm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Discovers all LlmProvider beans and orders them by priority.
 */
@Slf4j
@Component
public class LlmProviderFactory {

    private final List<LlmProvider> providers;

    public LlmProviderFactory(List<LlmProvider> allProviders) {
        this.providers = allProviders.stream()
                .filter(LlmProvider::isAvailable)
                .sorted(Comparator.comparingInt(LlmProvider::getPriority))
                .collect(Collectors.toList());

        if (providers.isEmpty()) {
            log.warn("⚠️  No LLM providers configured. AI features will use templates only.");
        } else {
            log.info("✅ LLM providers registered (in priority order): {}",
                    providers.stream().map(LlmProvider::getName).toList());
        }
    }

    public List<LlmProvider> getProviders() {
        return providers;
    }

    public boolean hasProviders() {
        return !providers.isEmpty();
    }
}