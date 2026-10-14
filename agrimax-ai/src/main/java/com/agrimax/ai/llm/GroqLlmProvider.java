package com.agrimax.ai.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Map;

/**
 * Groq LLM provider — uses Llama 3.3 70B via OpenAI-compatible API.
 * Free, ultra-fast, unlimited (14,400 req/day).
 * Priority 20 (fallback when Gemini rate-limited).
 */
@Slf4j
@Component
public class GroqLlmProvider implements LlmProvider {

    private static final String NAME = "groq";
    private static final int PRIORITY = 20;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String model;
    private final boolean enabled;

    public GroqLlmProvider(
            @Value("${agrimax.ai.groq.llm.api-key:${GROQ_API_KEY:}}") String apiKey,
            @Value("${agrimax.ai.groq.llm.base-url:https://api.groq.com/openai/v1}") String baseUrl,
            @Value("${agrimax.ai.groq.llm.model:llama-3.3-70b-versatile}") String model,
            @Value("${agrimax.ai.groq.llm.enabled:true}") boolean enabled,
            ObjectMapper objectMapper) {
        this.model = model;
        this.enabled = enabled && apiKey != null && !apiKey.isBlank();
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + (apiKey != null ? apiKey : ""))
                .build();
    }

    @Override public String getName() { return NAME; }
    @Override public int getPriority() { return PRIORITY; }
    @Override public boolean isAvailable() { return enabled; }

    @Override
    public String chat(String systemPrompt, String userMessage, double temperature, int maxTokens) {
        try {
            Map<String, Object> body = Map.of(
                    "model", model,
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", userMessage)
                    ),
                    "temperature", temperature,
                    "max_tokens", maxTokens
            );

            String responseJson = restClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(responseJson);
            String content = root.at("/choices/0/message/content").asText("");
            content = content.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();

            log.debug("[Groq] Success | model={} | contentLen={}", model, content.length());
            return content;

        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            log.warn("[Groq] HTTP {} — {}", status, truncate(e.getResponseBodyAsString()));
            throw new LlmException(NAME, status, "Groq HTTP " + status, e);
        } catch (Exception e) {
            log.warn("[Groq] Error: {}", e.getMessage());
            throw new LlmException(NAME, 0, "Groq error: " + e.getMessage(), e);
        }
    }

    private String truncate(String s) {
        if (s == null) return "";
        return s.length() > 200 ? s.substring(0, 200) + "..." : s;
    }
}