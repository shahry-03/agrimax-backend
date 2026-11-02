package com.agrimax.ai.tts;

import com.agrimax.ai.dto.request.TtsRequest;
import com.agrimax.ai.dto.response.TtsResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Base64;

/**
 * Enriches voice responses with TTS audio (base64).
 * Controllers call this after handler returns, so handlers stay pure.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VoiceResponseEnricher {

    private final TtsService ttsService;

    /**
     * Synthesize the appropriate message based on detected language.
     *
     * @return Audio data (base64 encoded), or {@link AudioPayload#none()} on failure
     */
    public AudioPayload enrich(String messageUr, String messageEn, String language) {
        String text = "en".equalsIgnoreCase(language) ? messageEn : messageUr;
        if (text == null || text.isBlank()) return AudioPayload.none();

        try {
            TtsResult result = ttsService.synthesize(TtsRequest.of(text, language));
            return AudioPayload.of(result);
        } catch (Exception e) {
            log.warn("TTS enrichment failed (fallback to text-only): {}", e.getMessage());
            return AudioPayload.none();
        }
    }

    /** Small payload record — what the frontend receives. */
    public record AudioPayload(
            String audioBase64,
            String format,
            String provider,
            String voiceId,
            boolean available
    ) {
        public static AudioPayload none() {
            return new AudioPayload(null, null, null, null, false);
        }

        public static AudioPayload of(TtsResult r) {
            return new AudioPayload(
                    Base64.getEncoder().encodeToString(r.audioBytes()),
                    r.contentType(),
                    r.provider(),
                    r.voiceId(),
                    true
            );
        }
    }
}