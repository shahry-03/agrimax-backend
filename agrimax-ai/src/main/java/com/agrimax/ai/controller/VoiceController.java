package com.agrimax.ai.controller;

import com.agrimax.ai.dto.request.VoiceCommandRequest;
import com.agrimax.ai.service.CommandRouterService;
import com.agrimax.ai.service.IntentDetectionService;
import com.agrimax.ai.service.SpeechToTextService;
import com.agrimax.common.dto.ApiResponse;
import com.agrimax.common.voice.CommandResult;
import com.agrimax.common.voice.VoiceIntent;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Voice command endpoint — the main entry point for farmer voice interactions.
 *
 * <p>Pipeline:</p>
 * <ol>
 *   <li>Text transcript input (mock STT mode)</li>
 *   <li>Gemini intent detection with session-aware conversation memory</li>
 *   <li>Command routing to module-specific handlers</li>
 *   <li>Bilingual (Urdu + English) response</li>
 * </ol>
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
     * POST /api/v1/voice/command
     * Accepts a text transcript (in mock STT mode) and processes it through the AI pipeline.
     * Works with or without authentication — unauthenticated users get limited functionality.
     */
    @PostMapping("/command")
    public ResponseEntity<ApiResponse<Map<String, Object>>> processVoiceCommand(
            @Valid @RequestBody VoiceCommandRequest request,
            Principal principal) {

        // Step 1: Speech-to-Text (mock — passes text directly)
        String transcript = sttService.transcribe(request.transcript());

        // Step 2: Determine session ID for conversation memory
        // Logged-in users get their userId as session; anonymous users get frontend-generated sessionId
        String sessionId;
        UUID userId;
        if (principal != null) {
            sessionId = principal.getName();
            userId = UUID.fromString(principal.getName());
        } else {
            sessionId = (request.sessionId() != null && !request.sessionId().isBlank())
                    ? request.sessionId()
                    : "anonymous";
            userId = null;
        }

        // Step 3: AI Intent Detection via Gemini (with caching + session memory)
        VoiceIntent intent = intentService.detectIntent(transcript, sessionId);
        log.info("Voice command processed — intent: {} | confidence: {} | lang: {} | session: {}",
                intent.intent(), intent.confidence(), intent.language(), sessionId);

        // Step 4: Inject sessionId into parameters so handlers can access session context
        // (Needed for LOGIN → VERIFY_OTP flow where phone number must be remembered)
        Map<String, Object> params = new HashMap<>(intent.parameters() != null ? intent.parameters() : Map.of());
        params.put("sessionId", sessionId);
        VoiceIntent enrichedIntent = new VoiceIntent(
                intent.intent(), params, intent.language(), intent.confidence()
        );

        // Step 5: Route to appropriate module handler
        CommandResult result = routerService.route(enrichedIntent, userId);

        // Step 6: Build comprehensive bilingual response
        Map<String, Object> responseData = new HashMap<>();
        responseData.put("intent", intent.intent());
        responseData.put("confidence", intent.confidence());
        responseData.put("language", intent.language());
        if (result.data() != null) {
            responseData.put("result", result.data());
        }

        if (result.success()) {
            return ResponseEntity.ok(
                    ApiResponse.success(responseData, result.messageEn(), result.messageUr())
            );
        } else {
            // Use 200 OK for business failures (e.g., crop not found) — frontend handles display
            return ResponseEntity.ok(
                    ApiResponse.<Map<String, Object>>builder()
                            .success(false)
                            .data(responseData)
                            .messageEn(result.messageEn())
                            .messageUr(result.messageUr())
                            .build()
            );
        }
    }
}