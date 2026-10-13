package com.agrimax.ai.service.whisper;

import com.agrimax.ai.exception.WhisperException;
import com.agrimax.ai.dto.request.AudioInput;
import com.agrimax.ai.dto.response.TranscriptionResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * Groq Whisper provider — free, ultra-fast transcription.
 * Uses whisper-large-v3 model. Auto-detects language.
 *
 * <p>API: https://console.groq.com/docs/speech-text</p>
 */
@Slf4j
@Component
public class GroqWhisperProvider implements WhisperProvider {

    private static final String NAME = "groq";
    private static final int PRIORITY = 10; // Try first (fastest, free)

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;
    private final boolean enabled;

    public GroqWhisperProvider(
            @Value("${agrimax.ai.whisper.groq.api-key:}") String apiKey,
            @Value("${agrimax.ai.whisper.groq.base-url:https://api.groq.com/openai/v1}") String baseUrl,
            @Value("${agrimax.ai.whisper.groq.model:whisper-large-v3}") String model,
            @Value("${agrimax.ai.whisper.groq.enabled:true}") boolean enabled,
            ObjectMapper objectMapper) {
        this.apiKey = apiKey;
        this.model = model;
        this.enabled = enabled && apiKey != null && !apiKey.isBlank();
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + (apiKey != null ? apiKey : ""))
                .build();
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public int getPriority() {
        return PRIORITY;
    }

    @Override
    public boolean isAvailable() {
        return enabled;
    }

    @Override
    public TranscriptionResult transcribe(AudioInput input) {
        if (!enabled) {
            throw new WhisperException("Groq Whisper provider is not configured");
        }

        try {
            long start = System.currentTimeMillis();

            // Build multipart form
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new ByteArrayResource(input.audioBytes()) {
                @Override
                public String getFilename() {
                    return input.filename() != null ? input.filename() : "audio.webm";
                }
            });
            body.add("model", model);
            body.add("response_format", "verbose_json"); // Gives us language + duration

            // Only pass language if user provided a hint (else auto-detect)
            if (input.hasLanguageHint()) {
                body.add("language", input.languageHint());
            }

            String responseJson = restClient.post()
                    .uri("/audio/transcriptions")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(responseJson);
            String text = root.path("text").asText("").trim();
            String language = root.path("language").asText("unknown");
            Double duration = root.has("duration") ? root.path("duration").asDouble() : null;

            long elapsed = System.currentTimeMillis() - start;
            log.info("[Groq Whisper] Transcribed {} bytes in {}ms | lang={} | text=\"{}\"",
                    input.audioBytes().length, elapsed, language, truncate(text));

            return new TranscriptionResult(text, normalizeLanguage(language), duration, NAME);

        } catch (Exception e) {
            log.error("[Groq Whisper] Transcription failed: {}", e.getMessage());
            throw new WhisperException("Groq Whisper failed: " + e.getMessage(), e);
        }
    }

    /** Normalize language codes to ISO 639-1 (Groq returns full names like "urdu") */
    private String normalizeLanguage(String language) {
        if (language == null) return "unknown";
        String lower = language.toLowerCase();
        return switch (lower) {
            case "urdu" -> "ur";
            case "english" -> "en";
            case "hindi" -> "hi";
            case "punjabi" -> "pa";
            case "sindhi" -> "sd";
            default -> lower.length() > 2 ? lower.substring(0, 2) : lower;
        };
    }

    private String truncate(String text) {
        if (text == null) return "";
        return text.length() > 100 ? text.substring(0, 100) + "..." : text;
    }
}