package com.agrimax.ai.websocket;

/** Lifecycle state of a single voice WebSocket session. */
public enum VoiceSessionState {
    IDLE,           // before "start" message
    LISTENING,      // waiting for audio chunks
    PROCESSING,     // STT → intent → handler → TTS running
    SPEAKING,       // audio sent, waiting for playback_done
    ENDED           // session closed
}