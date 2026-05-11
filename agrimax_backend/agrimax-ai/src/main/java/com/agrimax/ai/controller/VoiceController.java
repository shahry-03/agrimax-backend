package com.agrimax.ai.controller;

import com.agrimax.ai.dto.VoiceCommandRequest;
import com.agrimax.ai.service.CommandRouterService;
import com.agrimax.ai.service.IntentDetectionService;
import com.agrimax.ai.service.SpeechToTextService;
import com.agrimax.common.dto.ApiResponse;
import com.agrimax.common.voice.CommandResult;
import com.agrimax.common.voice.VoiceIntent;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Map;
import java.util.UUID;

/**
 * Voice command endpoint — the main entry point for farmer interactions.
 * Accepts text transcripts (mock STT) and routes them through the AI pipeline.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/voice")
@RequiredArgsConstructor
public class VoiceController {

    private final SpeechToTextService sttService;
    private final IntentDetectionService intentService;
    private final CommandRouterService routerService;

    /**
     * Process a voice command (as text transcript in mock mode).
     * Pipeline: Text → Intent Detection → Command Routing → Response
     */
    @PostMapping("/command")
    public ResponseEntity<ApiResponse<Map<String, Object>>> processVoiceCommand(
            @Valid @RequestBody VoiceCommandRequest request,
            Principal principal) {

        // Step 1: Speech-to-Text (mock — just passes through)
        String transcript = sttService.transcribe(request.transcript());

        // Step 2: AI Intent Detection via Gemini
        VoiceIntent intent = intentService.detectIntent(transcript);
        log.info("Detected: {} (confidence: {})", intent.intent(), intent.confidence());

        // Step 3: Route to appropriate module handler
        UUID userId = principal != null ? UUID.fromString(principal.getName()) : null;
        CommandResult result = routerService.route(intent, userId);

        // Step 4: Build bilingual response
        Map<String, Object> responseData = Map.of(
                "intent", intent.intent(),
                "confidence", intent.confidence(),
                "language", intent.language(),
                "result", result.data() != null ? result.data() : Map.of()
        );

        if (result.success()) {
            return ResponseEntity.ok(
                    ApiResponse.success(responseData, result.messageEn(), result.messageUr())
            );
        } else {
            return ResponseEntity.badRequest().body(
                    ApiResponse.error(result.messageEn(), result.messageEn(), result.messageUr())
            );
        }
    }
}
