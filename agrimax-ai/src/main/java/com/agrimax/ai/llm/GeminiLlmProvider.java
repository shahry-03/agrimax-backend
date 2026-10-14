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
 * Google Gemini LLM provider.
 * Priority 10 (tried first — highest quality).
 */
@Slf4j
@Component
public class GeminiLlmProvider implements LlmProvider {

    private static final String NAME = "gemini";
    private static final int PRIORITY = 10;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String model;
    private final boolean enabled;

    public GeminiLlmProvider(
            @Value("${agrimax.ai.gemini.api-key:}") String apiKey,
            @Value("${agrimax.ai.gemini.base-url:https://generativelanguage.googleapis.com/v1beta}") String baseUrl,
            @Value("${agrimax.ai.gemini.model:gemini-2.5-flash}") String model,
            @Value("${agrimax.ai.gemini.enabled:true}") boolean enabled,
            ObjectMapper objectMapper) {
        this.model = model;
        this.enabled = enabled && apiKey != null && !apiKey.isBlank();
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("x-goog-api-key", apiKey != null ? apiKey : "")
                .build();
    }

    @Override public String getName() { return NAME; }
    @Override public int getPriority() { return PRIORITY; }
    @Override public boolean isAvailable() { return enabled; }

    @Override
    public String chat(String systemPrompt, String userMessage, double temperature, int maxTokens) {
        try {
            Map<String, Object> body = Map.of(
                    "system_instruction", Map.of(
                            "parts", List.of(Map.of("text", systemPrompt))
                    ),
                    "contents", List.of(Map.of(
                            "parts", List.of(Map.of("text", userMessage))
                    )),
                    "generationConfig", Map.of(
                            "temperature", temperature,
                            "maxOutputTokens", maxTokens,
                            "responseMimeType", "application/json"
                    )
            );

            String responseJson = restClient.post()
                    .uri("/models/{model}:generateContent", model)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(responseJson);
            String content = root.at("/candidates/0/content/parts/0/text").asText();
            content = content.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();

            log.debug("[Gemini] Success | tokens≈{} | contentLen={}", maxTokens, content.length());
            return content;

        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            log.warn("[Gemini] HTTP {} — {}", status, truncate(e.getResponseBodyAsString()));
            throw new LlmException(NAME, status, "Gemini HTTP " + status, e);
        } catch (Exception e) {
            log.warn("[Gemini] Error: {}", e.getMessage());
            throw new LlmException(NAME, 0, "Gemini error: " + e.getMessage(), e);
        }
    }

    private String truncate(String s) {
        if (s == null) return "";
        return s.length() > 200 ? s.substring(0, 200) + "..." : s;
    }
}