package com.agrimax.ai.controller;

import com.agrimax.ai.dto.request.VoiceCommandRequest;
import com.agrimax.ai.service.CommandRouterService;
import com.agrimax.ai.service.IntentDetectionService;
import com.agrimax.ai.service.SpeechToTextService;
import com.agrimax.ai.tts.VoiceResponseEnricher;
import com.agrimax.common.dto.ApiResponse;
import com.agrimax.common.voice.CommandResult;
import com.agrimax.common.voice.UIAction;
import com.agrimax.common.voice.VoiceIntent;
import com.agrimax.identity.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/voice")
@RequiredArgsConstructor
public class VoiceController {

    private final SpeechToTextService sttService;
    private final IntentDetectionService intentService;
    private final CommandRouterService routerService;
    private final VoiceResponseEnricher voiceEnricher;

    @PostMapping("/command")
    public ResponseEntity<ApiResponse<Map<String, Object>>> processVoiceCommand(
            @Valid @RequestBody VoiceCommandRequest request,
            @AuthenticationPrincipal User user) {   // 🆕 null if anonymous

        // Step 1: STT
        String transcript = sttService.transcribe(request.transcript());

        // Step 2: Resolve identity
        String sessionId;
        UUID userId = null;
        String role = null;
        String preferredMode = null;

        if (user != null) {
            userId = user.getId();
            sessionId = userId.toString();
            role = user.getRole();
            preferredMode = user.getPreferredMode() != null
                    ? user.getPreferredMode().name() : null;
        } else {
            sessionId = (request.sessionId() != null && !request.sessionId().isBlank())
                    ? request.sessionId()
                    : "anonymous";
        }

        // Step 3: Intent detection (role-aware)
        VoiceIntent intent = intentService.detectIntent(transcript, sessionId, role);
        log.info("Voice — intent={} conf={} lang={} role={} session={}",
                intent.intent(), intent.confidence(), intent.language(),
                role != null ? role : "ANON", sessionId);

        // Step 4: Inject session + context
        Map<String, Object> params = new HashMap<>(
                intent.parameters() != null ? intent.parameters() : Map.of());
        params.put("sessionId", sessionId);

        if (request.contextPostId() != null && !request.contextPostId().isBlank()) {
            params.put("postId", request.contextPostId());
        }
        if (request.contextBidId() != null && !request.contextBidId().isBlank()) {
            params.put("bidId", request.contextBidId());
        }
        if (request.contextDealId() != null && !request.contextDealId().isBlank()) {
            params.put("dealId", request.contextDealId());
        }

        VoiceIntent enriched = new VoiceIntent(
                intent.intent(), params, intent.language(), intent.confidence());

        // Step 5: Route
        CommandResult result = routerService.route(enriched, userId);

        // Step 6: Build response
        Map<String, Object> responseData = new HashMap<>();
        responseData.put("intent", intent.intent());
        responseData.put("confidence", intent.confidence());
        responseData.put("language", intent.language());
        responseData.put("uiAction", toUiActionMap(result.uiAction()));

        // 🆕 Expose role + preferredMode so frontend can adapt UI
        responseData.put("userRole", role != null ? role : "ANONYMOUS");
        responseData.put("preferredMode", preferredMode != null ? preferredMode : "VOICE");

        if (result.data() != null) {
            responseData.put("result", result.data());
        }

        // Step 7: TTS
        var audio = voiceEnricher.enrich(
                result.messageUr(), result.messageEn(), intent.language());
        if (audio.available()) {
            responseData.put("audio", Map.of(
                    "base64", audio.audioBase64(),
                    "format", audio.format(),
                    "voiceId", audio.voiceId()));
        }

        // Step 8: Return
        if (result.success()) {
            return ResponseEntity.ok(ApiResponse.success(
                    responseData, result.messageEn(), result.messageUr()));
        }
        return ResponseEntity.ok(
                ApiResponse.<Map<String, Object>>builder()
                        .success(false)
                        .data(responseData)
                        .messageEn(result.messageEn())
                        .messageUr(result.messageUr())
                        .build());
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