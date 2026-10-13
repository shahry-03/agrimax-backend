package com.agrimax.ai.controller;

import com.agrimax.ai.service.CommandRouterService;
import com.agrimax.ai.service.IntentDetectionService;
import com.agrimax.ai.service.SpeechToTextService;
import com.agrimax.ai.dto.request.AudioInput;
import com.agrimax.ai.dto.response.TranscriptionResult;
import com.agrimax.common.dto.ApiResponse;
import com.agrimax.common.voice.CommandResult;
import com.agrimax.common.voice.VoiceIntent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Audio-based voice command endpoint.
 * Accepts audio file, transcribes via Whisper, then runs the same pipeline as /voice/command.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/voice")
@RequiredArgsConstructor
public class AudioController {

    private final SpeechToTextService sttService;
    private final IntentDetectionService intentService;
    private final CommandRouterService routerService;

    /**
     * POST /api/v1/voice/audio
     *
     * @param audio       Multipart audio file (webm/wav/mp3/m4a)
     * @param sessionId   Session ID for conversation memory
     * @param languageHint Optional ("ur" or "en") — leave empty for auto-detect
     */
    @PostMapping(value = "/audio", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Map<String, Object>>> processAudioCommand(
            @RequestParam("audio") MultipartFile audio,
            @RequestParam(value = "sessionId", required = false) String sessionId,
            @RequestParam(value = "languageHint", required = false) String languageHint,
            Principal principal) {

        try {
            // Validate
            if (audio.isEmpty()) {
                return ResponseEntity.badRequest().body(
                        ApiResponse.error("EMPTY_AUDIO", "Audio file is empty", "آڈیو فائل خالی ہے"));
            }

            // Build audio input
            AudioInput input = new AudioInput(
                    audio.getBytes(),
                    audio.getOriginalFilename(),
                    audio.getContentType(),
                    languageHint
            );

            // Step 1: Transcribe with Whisper (auto-detects language)
            TranscriptionResult transcription = sttService.transcribeAudio(input);
            String transcript = transcription.text();

            // Step 2: Resolve session ID (same logic as VoiceController)
            String resolvedSessionId;
            UUID userId;
            if (principal != null) {
                resolvedSessionId = principal.getName();
                userId = UUID.fromString(principal.getName());
            } else {
                resolvedSessionId = (sessionId != null && !sessionId.isBlank())
                        ? sessionId
                        : "anonymous-" + UUID.randomUUID();
                userId = null;
            }

            // Step 3: Detect intent
            VoiceIntent intent = intentService.detectIntent(transcript, resolvedSessionId);

            // Step 4: Inject sessionId into parameters (for multi-turn flows)
            Map<String, Object> params = new HashMap<>(
                    intent.parameters() != null ? intent.parameters() : Map.of()
            );
            params.put("sessionId", resolvedSessionId);
            VoiceIntent enriched = new VoiceIntent(
                    intent.intent(), params, transcription.language(), intent.confidence()
            );

            // Step 5: Route to handler
            CommandResult result = routerService.route(enriched, userId);

            // Step 6: Build response
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("transcript", transcript);
            responseData.put("detectedLanguage", transcription.language());
            responseData.put("provider", transcription.provider());
            responseData.put("intent", intent.intent());
            responseData.put("confidence", intent.confidence());
            if (result.data() != null) {
                responseData.put("result", result.data());
            }

            ApiResponse<Map<String, Object>> response = ApiResponse.<Map<String, Object>>builder()
                    .success(result.success())
                    .messageEn(result.messageEn())
                    .messageUr(result.messageUr())
                    .data(responseData)
                    .build();

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Audio command processing failed: {}", e.getMessage(), e);
            return ResponseEntity.ok(
                    ApiResponse.error("AUDIO_ERROR",
                            "Could not process your audio. Please try again.",
                            "آپ کی آڈیو پر عمل نہیں ہو سکا۔ دوبارہ کوشش کریں۔")
            );
        }
    }
}