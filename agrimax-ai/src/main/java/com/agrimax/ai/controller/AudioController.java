package com.agrimax.ai.controller;

import com.agrimax.ai.dto.request.AudioInput;
import com.agrimax.ai.dto.response.TranscriptionResult;
import com.agrimax.ai.service.CommandRouterService;
import com.agrimax.ai.service.IntentDetectionService;
import com.agrimax.ai.service.SpeechToTextService;
import com.agrimax.ai.tts.VoiceResponseEnricher;
import com.agrimax.common.dto.ApiResponse;
import com.agrimax.common.voice.CommandResult;
import com.agrimax.common.voice.UIAction;
import com.agrimax.common.voice.VoiceIntent;
import com.agrimax.identity.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/voice")
@RequiredArgsConstructor
public class AudioController {

    private final SpeechToTextService sttService;
    private final IntentDetectionService intentService;
    private final CommandRouterService routerService;
    private final VoiceResponseEnricher voiceEnricher;

    @PostMapping(value = "/audio", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Map<String, Object>>> processAudioCommand(
            @RequestParam("audio") MultipartFile audio,
            @RequestParam(value = "sessionId", required = false) String sessionId,
            @RequestParam(value = "languageHint", required = false) String languageHint,
            @AuthenticationPrincipal User user) {   // 🆕

        try {
            if (audio.isEmpty()) {
                return ResponseEntity.badRequest().body(
                        ApiResponse.error("EMPTY_AUDIO",
                                "Audio file is empty", "آڈیو فائل خالی ہے"));
            }

            AudioInput input = new AudioInput(
                    audio.getBytes(), audio.getOriginalFilename(),
                    audio.getContentType(), languageHint);

            // Step 1: STT
            TranscriptionResult transcription = sttService.transcribeAudio(input);
            String transcript = transcription.text();

            // Step 2: Resolve identity
            String resolvedSessionId;
            UUID userId = null;
            String role = null;

            if (user != null) {
                userId = user.getId();
                resolvedSessionId = userId.toString();
                role = user.getRole();
            } else {
                resolvedSessionId = (sessionId != null && !sessionId.isBlank())
                        ? sessionId
                        : "anonymous-" + UUID.randomUUID();
            }

            // Step 3: Intent (role-aware)
            VoiceIntent intent = intentService.detectIntent(
                    transcript, resolvedSessionId, role);

            // Step 4: Enrich params
            Map<String, Object> params = new HashMap<>(
                    intent.parameters() != null ? intent.parameters() : Map.of());
            params.put("sessionId", resolvedSessionId);
            VoiceIntent enriched = new VoiceIntent(
                    intent.intent(), params, transcription.language(), intent.confidence());

            // Step 5: Route
            CommandResult result = routerService.route(enriched, userId);

            // Step 6: Response
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("transcript", transcript);
            responseData.put("detectedLanguage", transcription.language());
            responseData.put("provider", transcription.provider());
            responseData.put("intent", intent.intent());
            responseData.put("confidence", intent.confidence());
            responseData.put("uiAction", toUiActionMap(result.uiAction()));
            responseData.put("userRole", role != null ? role : "ANONYMOUS");
            if (result.data() != null) responseData.put("result", result.data());

            var voiceAudio = voiceEnricher.enrich(
                    result.messageUr(), result.messageEn(), transcription.language());
            if (voiceAudio.available()) {
                responseData.put("audio", Map.of(
                        "base64", voiceAudio.audioBase64(),
                        "format", voiceAudio.format(),
                        "voiceId", voiceAudio.voiceId()));
            }

            return ResponseEntity.ok(
                    ApiResponse.<Map<String, Object>>builder()
                            .success(result.success())
                            .messageEn(result.messageEn())
                            .messageUr(result.messageUr())
                            .data(responseData)
                            .build());

        } catch (Exception e) {
            log.error("Audio command failed: {}", e.getMessage(), e);
            return ResponseEntity.ok(
                    ApiResponse.error("AUDIO_ERROR",
                            "Could not process your audio. Please try again.",
                            "آپ کی آڈیو پر عمل نہیں ہو سکا۔ دوبارہ کوشش کریں۔"));
        }
    }

    private Map<String, Object> toUiActionMap(UIAction uiAction) {
        if (uiAction == null) return Map.of("type", "NONE");
        Map<String, Object> m = new HashMap<>();
        m.put("type", uiAction.type() != null ? uiAction.type().name() : "NONE");
        if (uiAction.target() != null) m.put("target", uiAction.target());
        if (uiAction.params() != null && !uiAction.params().isEmpty())
            m.put("params", uiAction.params());
        return m;
    }
}