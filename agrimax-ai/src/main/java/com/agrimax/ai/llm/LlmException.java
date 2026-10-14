package com.agrimax.ai.llm;

/**
 * Thrown when LLM provider fails.
 * Carries the HTTP status code for circuit-breaker logic.
 */
public class LlmException extends RuntimeException {

    private final int statusCode;      // 429, 500, etc. (0 if unknown)
    private final String providerName;

    /** Full constructor with status code and cause. */
    public LlmException(String providerName, int statusCode, String message, Throwable cause) {
        super(message, cause);
        this.providerName = providerName;
        this.statusCode = statusCode;
    }

    /** Simple constructor — no status code, no cause. */
    public LlmException(String providerName, String message) {
        this(providerName, 0, message, null);
    }

    /** Constructor with cause but no status code — used for wrap-and-throw. */
    public LlmException(String providerName, String message, Throwable cause) {
        this(providerName, 0, message, cause);
    }

    public int getStatusCode() { return statusCode; }
    public String getProviderName() { return providerName; }

    /** Rate-limited or quota-exceeded */
    public boolean isRateLimited() {
        return statusCode == 429 || statusCode == 503;
    }
}