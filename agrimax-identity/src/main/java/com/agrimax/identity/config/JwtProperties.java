package com.agrimax.identity.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Binds to the EXISTING agrimax.jwt.* config keys (backward compatible).
 * No yaml changes needed for JWT.
 */
@Component
@ConfigurationProperties(prefix = "agrimax.jwt")
@Getter @Setter
public class JwtProperties {

    /**
     * Secret key. Accepts EITHER raw string OR base64-encoded string.
     * Must decode to at least 32 bytes (256 bits) for HS256.
     */
    private String secret;

    private long accessTokenExpiryMs = 86_400_000L;      // 1 day (matches old default)
    private long refreshTokenExpiryMs = 604_800_000L;    // 7 days
    private String issuer = "agrimax";
}