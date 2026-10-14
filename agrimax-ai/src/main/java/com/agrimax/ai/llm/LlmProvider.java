package com.agrimax.ai.llm;

/**
 * Strategy interface for LLM providers (Gemini, Groq, etc.).
 * Each provider is a Spring bean; HybridLlmService auto-discovers them.
 */
public interface LlmProvider {

    /** Provider name (e.g., "gemini", "groq") */
    String getName();

    /** Lower value = higher priority (tried first) */
    int getPriority();

    /** true if provider is properly configured (API key present + enabled) */
    boolean isAvailable();

    /**
     * Send a chat request to the LLM.
     *
     * @param systemPrompt  System-level instructions
     * @param userMessage   User query
     * @param temperature   0.0 (deterministic) to 1.0 (creative)
     * @param maxTokens     Max response tokens
     * @return Raw text response (should be JSON-formatted if prompt requests it)
     * @throws LlmException on failure
     */
    String chat(String systemPrompt, String userMessage, double temperature, int maxTokens);
}