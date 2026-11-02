package com.agrimax.common.ratelimit;

public interface RateLimitService {

    boolean tryConsume(String bucketName, String clientKey, RateLimit config);
    long getRemainingTokens(String bucketName, String clientKey, RateLimit config);
    void reset(String bucketName, String clientKey);
    void resetAll();
}