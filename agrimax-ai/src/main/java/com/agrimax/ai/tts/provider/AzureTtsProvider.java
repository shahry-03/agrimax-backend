package com.agrimax.ai.tts.provider;

import com.agrimax.ai.dto.request.TtsRequest;
import com.agrimax.ai.dto.response.TtsResult;
import com.agrimax.ai.exception.TtsException;
import com.agrimax.ai.tts.TtsProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Azure Cognitive Services TTS provider — PRIMARY.
 *
 * <p>Voices:</p>
 * <ul>
 *   <li>Urdu: ur-PK-UzmaNeural (female), ur-PK-AsadNeural (male)</li>
 *   <li>English: en-US-JennyNeural (female), en-US-GuyNeural (male)</li>
 * </ul>
 *
 * <p>Docs: https://learn.microsoft.com/azure/ai-services/speech-service/rest-text-to-speech</p>
 */
@Slf4j
@Component
public class AzureTtsProvider implements TtsProvider {

    private static final String NAME = "azure";
    private static final int PRIORITY = 10; // Primary

    private static final String VOICE_UR_FEMALE = "ur-PK-UzmaNeural";
    private static final String VOICE_UR_MALE   = "ur-PK-AsadNeural";
    private static final String VOICE_EN_FEMALE = "en-US-JennyNeural";
    private static final String VOICE_EN_MALE   = "en-US-GuyNeural";

    private final RestClient restClient;
    private final String defaultVoiceUr;
    private final String defaultVoiceEn;
    private final String outputFormat;
    private final boolean enabled;

    public AzureTtsProvider(
            @Value("${agrimax.ai.tts.azure.api-key:}") String apiKey,
            @Value("${agrimax.ai.tts.azure.region:}") String region,
            @Value("${agrimax.ai.tts.azure.output-format:audio-24khz-48kbitrate-mono-mp3}") String outputFormat,
            @Value("${agrimax.ai.tts.azure.enabled:true}") boolean enabled,
            @Value("${agrimax.ai.tts.default-voice-ur:ur-PK-UzmaNeural}") String defaultVoiceUr,
            @Value("${agrimax.ai.tts.default-voice-en:en-US-JennyNeural}") String defaultVoiceEn) {

        this.outputFormat = outputFormat;
        this.defaultVoiceUr = defaultVoiceUr;
        this.defaultVoiceEn = defaultVoiceEn;
        this.enabled = enabled
                && apiKey != null && !apiKey.isBlank()
                && region != null && !region.isBlank();

        String baseUrl = "https://" + (region != null ? region : "eastus")
                + ".tts.speech.microsoft.com/cognitiveservices/v1";

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Ocp-Apim-Subscription-Key", apiKey != null ? apiKey : "")
                .defaultHeader("User-Agent", "AgriMax-Voice/1.0")
                .build();

        if (!enabled) {
            log.warn("⚠️  Azure TTS provider disabled (missing api-key or region)");
        }
    }

    @Override public String getName() { return NAME; }
    @Override public int getPriority() { return PRIORITY; }
    @Override public boolean isAvailable() { return enabled; }

    @Override
    public boolean supportsLanguage(String language) {
        return "ur".equalsIgnoreCase(language) || "en".equalsIgnoreCase(language);
    }

    @Override
    public TtsResult synthesize(TtsRequest request) {
        if (!enabled) {
            throw new TtsException("Azure TTS is not configured");
        }
        if (request.text() == null || request.text().isBlank()) {
            throw new TtsException("Cannot synthesize empty text");
        }

        try {
            String voice = resolveVoice(request);
            String voiceLang = extractVoiceLanguage(voice);
            String ssml = buildSsml(request, voice, voiceLang);

            long start = System.currentTimeMillis();

            byte[] audioBytes = restClient.post()
                    .contentType(MediaType.parseMediaType("application/ssml+xml"))
                    .header("X-Microsoft-OutputFormat", outputFormat)
                    .body(ssml)
                    .retrieve()
                    .body(byte[].class);

            long elapsed = System.currentTimeMillis() - start;

            if (audioBytes == null || audioBytes.length == 0) {
                throw new TtsException("Azure returned empty audio");
            }

            log.info("[Azure TTS] {} chars → {} bytes in {}ms | voice={}",
                    request.text().length(), audioBytes.length, elapsed, voice);

            return new TtsResult(
                    audioBytes,
                    "audio/mpeg",
                    null,          // duration unknown (Azure doesn't return in MP3)
                    NAME,
                    voice
            );

        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            String body = e.getResponseBodyAsString();
            log.error("[Azure TTS] HTTP {}: {}", status, truncate(body));
            throw new TtsException("Azure TTS failed (HTTP " + status + ")", e);
        } catch (TtsException e) {
            throw e;
        } catch (Exception e) {
            log.error("[Azure TTS] Error: {}", e.getMessage(), e);
            throw new TtsException("Azure TTS error: " + e.getMessage(), e);
        }
    }

    // ── Helpers ──

    private String resolveVoice(TtsRequest request) {
        // 1. Explicit voice ID wins
        if (request.voiceId() != null && !request.voiceId().isBlank()) {
            return request.voiceId();
        }
        // 2. Gender hint
        if (request.isUrdu()) {
            return "male".equalsIgnoreCase(request.gender()) ? VOICE_UR_MALE : defaultVoiceUr;
        }
        return "male".equalsIgnoreCase(request.gender()) ? VOICE_EN_MALE : defaultVoiceEn;
    }

    /** "ur-PK-UzmaNeural" → "ur-PK" */
    private String extractVoiceLanguage(String voice) {
        int lastDash = voice.lastIndexOf('-');
        return lastDash > 0 ? voice.substring(0, lastDash) : voice;
    }

    private String buildSsml(TtsRequest request, String voice, String voiceLang) {
        String escaped = escapeXml(request.text());
        int percent = (int) Math.round((request.effectiveSpeed() - 1.0) * 100);
        String rateStr = (percent >= 0 ? "+" : "") + percent + "%";

        return "<speak version='1.0' xml:lang='" + voiceLang + "'>"
                + "<voice xml:lang='" + voiceLang + "' name='" + voice + "'>"
                + "<prosody rate='" + rateStr + "'>" + escaped + "</prosody>"
                + "</voice></speak>";
    }

    /** SSML requires XML escaping. */
    private String escapeXml(String text) {
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private String truncate(String s) {
        if (s == null) return "";
        return s.length() > 200 ? s.substring(0, 200) + "..." : s;
    }
}