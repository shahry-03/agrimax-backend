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
 * OpenAI Whisper provider — fallback if Groq fails.
 * Paid but highest accuracy.
 */
@Slf4j
@Component
public class OpenAIWhisperProvider implements WhisperProvider {

    private static final String NAME = "openai";
    private static final int PRIORITY = 20;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String model;
    private final boolean enabled;

    public OpenAIWhisperProvider(
            @Value("${agrimax.ai.whisper.openai.api-key:}") String apiKey,
            @Value("${agrimax.ai.whisper.openai.base-url:https://api.openai.com/v1}") String baseUrl,
            @Value("${agrimax.ai.whisper.openai.model:whisper-1}") String model,
            @Value("${agrimax.ai.whisper.openai.enabled:false}") boolean enabled,
            ObjectMapper objectMapper) {
        this.model = model;
        this.enabled = enabled && apiKey != null && !apiKey.isBlank();
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + (apiKey != null ? apiKey : ""))
                .build();
    }

    @Override
    public String getName() { return NAME; }

    @Override
    public int getPriority() { return PRIORITY; }

    @Override
    public boolean isAvailable() { return enabled; }

    @Override
    public TranscriptionResult transcribe(AudioInput input) {
        if (!enabled) {
            throw new WhisperException("OpenAI Whisper provider is not configured");
        }

        try {
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new ByteArrayResource(input.audioBytes()) {
                @Override
                public String getFilename() {
                    return input.filename() != null ? input.filename() : "audio.webm";
                }
            });
            body.add("model", model);
            body.add("response_format", "verbose_json");

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

            log.info("[OpenAI Whisper] Transcribed | lang={} | text=\"{}\"", language, text);
            return new TranscriptionResult(text, language, duration, NAME);

        } catch (Exception e) {
            log.error("[OpenAI Whisper] Transcription failed: {}", e.getMessage());
            throw new WhisperException("OpenAI Whisper failed: " + e.getMessage(), e);
        }
    }
}