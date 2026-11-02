package com.agrimax.ai.websocket;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Per-connection WebSocket state.
 * Thread-safe — audio chunks and flags accessed from multiple threads.
 */
@Getter
public class VoiceSession {

        private final String socketId;
    private final String sessionId;
    private final UUID userId;               // null = anonymous
    private final String languageHint;
    private final String token;

    @Setter private String role;             // 🆕 "FARMER"|"BUYER"|"ADMIN"|null

    @Setter private volatile VoiceSessionState state = VoiceSessionState.IDLE;
    @Setter private volatile boolean interrupted = false;
    @Setter private volatile boolean processing = false;

    /** Audio chunks for the current utterance (WebM/Opus). */
    private final List<byte[]> audioChunks = new CopyOnWriteArrayList<>();

    /** Mutex for WebSocket send (Spring WS is not send-thread-safe). */
    private final Object sendLock = new Object();

    public VoiceSession(String socketId, String sessionId, UUID userId,
                        String languageHint, String token) {
        this.socketId = socketId;
        this.sessionId = sessionId;
        this.userId = userId;
        this.languageHint = languageHint;
        this.token = token;
    }

    public void addAudioChunk(byte[] chunk) {
        if (chunk != null && chunk.length > 0) audioChunks.add(chunk);
    }

    public byte[] drainAudio() {
        if (audioChunks.isEmpty()) return new byte[0];
        int total = audioChunks.stream().mapToInt(a -> a.length).sum();
        byte[] combined = new byte[total];
        int offset = 0;
        for (byte[] chunk : audioChunks) {
            System.arraycopy(chunk, 0, combined, offset, chunk.length);
            offset += chunk.length;
        }
        audioChunks.clear();
        return combined;
    }

    public int currentAudioSize() {
        return audioChunks.stream().mapToInt(a -> a.length).sum();
    }

    public Object getSendLock() { return sendLock; }
}