package com.agrimax.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Type-safe binding for agrimax.ai.whisper.* properties.
 * (Optional — providers read via @Value directly, but this is nice for future use.)
 */
@Component
@ConfigurationProperties(prefix = "agrimax.ai.whisper")
public class WhisperProperties {

    private int maxAudioSizeMb = 25;
    private String defaultLanguage = null; // null = auto-detect

    // getters/setters
    public int getMaxAudioSizeMb() { return maxAudioSizeMb; }
    public void setMaxAudioSizeMb(int maxAudioSizeMb) { this.maxAudioSizeMb = maxAudioSizeMb; }

    public String getDefaultLanguage() { return defaultLanguage; }
    public void setDefaultLanguage(String defaultLanguage) { this.defaultLanguage = defaultLanguage; }
}