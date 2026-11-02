package com.agrimax.ai.websocket;

import com.agrimax.ai.dto.request.AudioInput;
import com.agrimax.ai.dto.response.TranscriptionResult;
import com.agrimax.ai.service.CommandRouterService;
import com.agrimax.ai.service.IntentDetectionService;
import com.agrimax.ai.service.SpeechToTextService;
import com.agrimax.ai.tts.VoiceResponseEnricher;
import com.agrimax.common.voice.CommandResult;
import com.agrimax.common.voice.UIAction;
import com.agrimax.common.voice.VoiceIntent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;
import com.agrimax.identity.repository.UserRepository;
import com.agrimax.identity.service.JwtService;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Continuous voice WebSocket handler.
 *
 * <p>Farmer speaks → transcript → intent → handler → TTS → auto-navigate.
 * Loop runs until user says "stop/ruko/bas" or closes mic.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VoiceWebSocketHandler extends AbstractWebSocketHandler {

    private final SpeechToTextService sttService;
    private final IntentDetectionService intentService;
    private final CommandRouterService routerService;
    private final VoiceResponseEnricher voiceEnricher;
    private final ObjectMapper objectMapper;
    private final JwtService jwtService;
    private final UserRepository userRepository;

    @Qualifier("voiceWsExecutor")
    private final ThreadPoolTaskExecutor executor;

    private final Map<String, VoiceSession> sessions = new ConcurrentHashMap<>();

    // ═══════════════════════════════════════════════════════
    // LIFECYCLE
    // ═══════════════════════════════════════════════════════

    @Override
    public void afterConnectionEstablished(WebSocketSession socket) throws Exception {
        log.info("🔌 WS connected: {}", socket.getId());
        sendJson(socket, Map.of("type", "connected", "socketId", socket.getId()));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession socket, CloseStatus status) {
        VoiceSession session = sessions.remove(socket.getId());
        if (session != null) session.setState(VoiceSessionState.ENDED);
        log.info("🔌 WS closed: {} ({})", socket.getId(), status);
    }

    // ═══════════════════════════════════════════════════════
    // BINARY FRAMES = audio chunks
    // ═══════════════════════════════════════════════════════

    @Override
    protected void handleBinaryMessage(WebSocketSession socket, BinaryMessage message) {
        VoiceSession session = sessions.get(socket.getId());
        if (session == null || session.getState() == VoiceSessionState.ENDED) {
            log.debug("Dropping binary frame — no active session");
            return;
        }
        if (session.getState() != VoiceSessionState.LISTENING) {
            log.debug("Dropping binary frame — state is {}", session.getState());
            return;
        }
        ByteBuffer buf = message.getPayload();
        byte[] chunk = new byte[buf.remaining()];
        buf.get(chunk);
        session.addAudioChunk(chunk);
    }

    // ═══════════════════════════════════════════════════════
    // TEXT FRAMES = control messages
    // ═══════════════════════════════════════════════════════

    @Override
    protected void handleTextMessage(WebSocketSession socket, TextMessage message) {
        try {
            JsonNode node = objectMapper.readTree(message.getPayload());
            String type = node.path("type").asText();
            log.debug("WS msg [{}] type={}", socket.getId(), type);

            switch (type) {
                case "start"         -> handleStart(socket, node);
                case "audio_end"     -> handleAudioEnd(socket, node);
                case "interrupt"     -> handleInterrupt(socket);
                case "playback_done" -> handlePlaybackDone(socket);
                case "stop"          -> handleStop(socket);
                case "cancel"        -> handleCancel(socket);
                default              -> sendError(socket, "UNKNOWN_TYPE", "Unknown type: " + type);
            }
        } catch (Exception e) {
            log.error("WS text msg error", e);
            sendError(socket, "PARSE_ERROR", "Could not parse message");
        }
    }

    // ═══════════════════════════════════════════════════════
    // START
    // ═══════════════════════════════════════════════════════

    private void handleStart(WebSocketSession socket, JsonNode node) throws IOException {
        String sessionId = node.path("sessionId").asText(null);
        String languageHint = node.path("languageHint").asText(null);
        String token = node.path("token").asText(null);

        UUID userId = null;
        String role = null;

        // 🆕 Validate JWT if provided
        if (token != null && !token.isBlank()) {
            try {
                if (jwtService.isAccessToken(token)) {
                    userId = jwtService.getUserId(token);
                    final UUID uid = userId;
                    var userOpt = userRepository.findById(uid);
                    if (userOpt.isPresent()) {
                        var user = userOpt.get();
                        if (user.isEnabled()) {
                            role = user.getRole();
                            log.info("WS auth: userId={} role={}", userId, role);
                        } else {
                            log.warn("WS auth: userId={} is disabled — proceeding anonymous", userId);
                            userId = null;
                        }
                    } else {
                        log.warn("WS auth: userId={} not found — proceeding anonymous", userId);
                        userId = null;
                    }
                } else {
                    log.debug("WS token present but not an access token");
                }
            } catch (Exception e) {
                log.debug("WS token validation failed: {}", e.getMessage());
            }
        }

        // If no token-derived sessionId, use client's or generate
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = userId != null ? userId.toString() : "ws-" + socket.getId();
        }

        VoiceSession session = new VoiceSession(
                socket.getId(), sessionId, userId, languageHint, token);
        session.setRole(role);                    // 🆕
        session.setState(VoiceSessionState.LISTENING);
        sessions.put(socket.getId(), session);

        log.info("▶️ Voice session started: {} (sessionId={}, role={})",
                socket.getId(), sessionId, role != null ? role : "ANON");

        sendState(socket, VoiceSessionState.LISTENING);
    }

    // ═══════════════════════════════════════════════════════
    // AUDIO_END → run pipeline async
    // ═══════════════════════════════════════════════════════

    private void handleAudioEnd(WebSocketSession socket, JsonNode node) {
        VoiceSession session = sessions.get(socket.getId());
        if (session == null) { sendError(socket, "NO_SESSION", "Send 'start' first"); return; }
        if (session.getState() != VoiceSessionState.LISTENING) {
            log.debug("Ignoring audio_end — state is {}", session.getState());
            return;
        }

        byte[] audio = session.drainAudio();
        if (audio.length == 0) {
            sendError(socket, "EMPTY_AUDIO", "No audio received");
            return;
        }

        String mimeType = node.path("mimeType").asText("audio/webm");
        session.setProcessing(true);
        session.setInterrupted(false);
        session.setState(VoiceSessionState.PROCESSING);
        sendState(socket, VoiceSessionState.PROCESSING);

        executor.execute(() -> processUtterance(socket, session, audio, mimeType));
    }

    // ═══════════════════════════════════════════════════════
    // PIPELINE
    // ═══════════════════════════════════════════════════════

    private void processUtterance(WebSocketSession socket, VoiceSession session,
                                  byte[] audio, String mimeType) {
        long start = System.currentTimeMillis();
        try {
            if (!socket.isOpen()) return;

            // ── STT ──
            AudioInput input = new AudioInput(
                    audio, "utterance." + ext(mimeType), mimeType, session.getLanguageHint());
            TranscriptionResult tr = sttService.transcribeAudio(input);
            String transcript = tr.text();
            log.info("🎤 [{}] → \"{}\" ({})", session.getSocketId(), transcript, tr.language());

            if (transcript == null || transcript.isBlank()) {
                sendError(socket, "EMPTY_TRANSCRIPT", "Could not understand audio");
                resetToListening(socket, session);
                return;
            }

            // ── INTENT ──
            VoiceIntent intent = intentService.detectIntent(
            transcript, session.getSessionId(), session.getRole());
            log.info("🧠 [{}] intent={} conf={} lang={}",
                    session.getSocketId(), intent.intent(),
                    intent.confidence(), intent.language());

            // ── ENRICH PARAMS ──
            Map<String, Object> params = new HashMap<>(
                    intent.parameters() != null ? intent.parameters() : Map.of());
            params.put("sessionId", session.getSessionId());
            params.put("raw_text", transcript);
            VoiceIntent enriched = new VoiceIntent(
                    intent.intent(), params, tr.language(), intent.confidence());

            // ── ROUTE ──
            CommandResult result = routerService.route(enriched, session.getUserId());

            // ── CHECK INTERRUPTED (user started new utterance during processing) ──
            if (session.isInterrupted()) {
                log.info("⚡ [{}] Interrupted — discarding response", session.getSocketId());
                session.setInterrupted(false);
                session.setProcessing(false);
                resetToListening(socket, session);
                return;
            }

            // ── CHECK STOP_LISTENING ──
            boolean sessionEnd = result.uiAction() != null
                    && result.uiAction().type() == UIAction.ActionType.STOP_LISTENING;

            // ── TTS ──
            var audioPayload = voiceEnricher.enrich(
                    result.messageUr(), result.messageEn(), tr.language());

            // ── BUILD RESPONSE ──
            Map<String, Object> response = new HashMap<>();
            response.put("type", "response");
            response.put("success", result.success());
            response.put("intent", intent.intent());
            response.put("confidence", intent.confidence());
            response.put("language", tr.language());
            response.put("transcript", transcript);
            response.put("messageEn", result.messageEn());
            response.put("messageUr", result.messageUr());
            response.put("uiAction", toUiActionMap(result.uiAction()));
            if (result.data() != null) response.put("result", result.data());
            if (audioPayload.available()) {
                response.put("audio", Map.of(
                        "base64", audioPayload.audioBase64(),
                        "format", audioPayload.format(),
                        "voiceId", audioPayload.voiceId()));
            }

            sendJson(socket, response);

            long elapsed = System.currentTimeMillis() - start;
            log.info("✅ [{}] response in {}ms (audio={})",
                    session.getSocketId(), elapsed, audioPayload.available());

            // ── STATE TRANSITION ──
            if (sessionEnd) {
                session.setState(VoiceSessionState.ENDED);
                sendJson(socket, Map.of("type", "session_ended", "reason", "user_stop"));
                safeClose(socket, CloseStatus.NORMAL);
            } else if (audioPayload.available()) {
                session.setState(VoiceSessionState.SPEAKING);
                sendState(socket, VoiceSessionState.SPEAKING);
                // Wait for playback_done from client
            } else {
                // No TTS — go back to listening immediately
                resetToListening(socket, session);
            }

        } catch (Exception e) {
            log.error("❌ [{}] Pipeline error: {}", session.getSocketId(), e.getMessage(), e);
            sendError(socket, "PIPELINE_ERROR", "Could not process audio");
            resetToListening(socket, session);
        } finally {
            session.setProcessing(false);
        }
    }

    // ═══════════════════════════════════════════════════════
    // INTERRUPT / PLAYBACK_DONE / STOP / CANCEL
    // ═══════════════════════════════════════════════════════

    private void handleInterrupt(WebSocketSession socket) {
        VoiceSession session = sessions.get(socket.getId());
        if (session == null) return;
        log.info("⚡ [{}] Interrupt (state={})", session.getSocketId(), session.getState());

        if (session.getState() == VoiceSessionState.SPEAKING) {
            // Client is playing audio — switch back to listening, discard new audio buffer
            session.drainAudio(); // clear any buffered audio from prior
            resetToListening(socket, session);
        } else if (session.getState() == VoiceSessionState.PROCESSING) {
            // Async task still running — flag it, it will discard response
            session.setInterrupted(true);
            sendState(socket, VoiceSessionState.LISTENING);
        }
    }

    private void handlePlaybackDone(WebSocketSession socket) {
        VoiceSession session = sessions.get(socket.getId());
        if (session == null) return;
        if (session.getState() == VoiceSessionState.SPEAKING) {
            resetToListening(socket, session);
        }
    }

    private void handleStop(WebSocketSession socket) throws IOException {
        VoiceSession session = sessions.get(socket.getId());
        if (session == null) { safeClose(socket, CloseStatus.NORMAL); return; }
        log.info("🛑 [{}] Client requested stop", session.getSocketId());
        session.setState(VoiceSessionState.ENDED);
        sendJson(socket, Map.of("type", "session_ended", "reason", "client_stop"));
        safeClose(socket, CloseStatus.NORMAL);
    }

    private void handleCancel(WebSocketSession socket) {
        VoiceSession session = sessions.get(socket.getId());
        if (session == null) return;
        session.drainAudio(); // discard current utterance
        if (session.getState() == VoiceSessionState.LISTENING) {
            sendState(socket, VoiceSessionState.LISTENING); // ack
        }
    }

    // ═══════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════

    private void resetToListening(WebSocketSession socket, VoiceSession session) {
        if (session.getState() == VoiceSessionState.ENDED) return;
        session.setState(VoiceSessionState.LISTENING);
        sendState(socket, VoiceSessionState.LISTENING);
    }

    private void sendState(WebSocketSession socket, VoiceSessionState state) {
        sendJson(socket, Map.of("type", "state", "state", state.name()));
    }

    private void sendError(WebSocketSession socket, String code, String message) {
        sendJson(socket, Map.of(
                "type", "error", "code", code, "message", message));
    }

    private void sendJson(WebSocketSession socket, Map<String, Object> payload) {
        if (!socket.isOpen()) return;
        VoiceSession session = sessions.get(socket.getId());
        Object lock = session != null ? session.getSendLock() : socket;
        try {
            String json = objectMapper.writeValueAsString(payload);
            synchronized (lock) {
                if (socket.isOpen()) {
                    socket.sendMessage(new TextMessage(json));
                }
            }
        } catch (IOException e) {
            log.error("WS send failed: {}", e.getMessage());
        }
    }

    private Map<String, Object> toUiActionMap(UIAction uiAction) {
        if (uiAction == null) return Map.of("type", "NONE");
        Map<String, Object> m = new HashMap<>();
        m.put("type", uiAction.type().name());
        if (uiAction.target() != null) m.put("target", uiAction.target());
        if (uiAction.params() != null && !uiAction.params().isEmpty())
            m.put("params", uiAction.params());
        return m;
    }

    private void safeClose(WebSocketSession socket, CloseStatus status) {
        try { if (socket.isOpen()) socket.close(status); }
        catch (IOException ignored) {}
    }

    private String ext(String mime) {
        if (mime == null) return "webm";
        if (mime.contains("wav")) return "wav";
        if (mime.contains("mp3") || mime.contains("mpeg")) return "mp3";
        if (mime.contains("ogg")) return "ogg";
        if (mime.contains("m4a") || mime.contains("mp4")) return "m4a";
        return "webm";
    }
}