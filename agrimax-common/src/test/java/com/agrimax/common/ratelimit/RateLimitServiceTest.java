package com.agrimax.common.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class RateLimitServiceTest {

    private RateLimitServiceImpl rateLimitService;

    @RateLimit(name = "test-bucket", capacity = 2, refillTokens = 2, refillPeriod = 1, refillUnit = RateLimit.RefillUnit.MINUTES)
    public void dummyMethod() {}

    private RateLimit getDummyRateLimitConfig() throws NoSuchMethodException {
        Method method = RateLimitServiceTest.class.getMethod("dummyMethod");
        return method.getAnnotation(RateLimit.class);
    }

    @BeforeEach
    void setUp() {
        rateLimitService = new RateLimitServiceImpl();
    }

    @Test
    void testTokenConsumptionAndExhaustion() throws Exception {
        RateLimit config = getDummyRateLimitConfig();
        String clientKey = "192.168.1.100";

        // First two consumes should succeed (capacity = 2)
        assertTrue(rateLimitService.tryConsume(config.name(), clientKey, config));
        assertTrue(rateLimitService.tryConsume(config.name(), clientKey, config));

        // Third consume should fail as bucket is exhausted
        assertFalse(rateLimitService.tryConsume(config.name(), clientKey, config));
    }

    @Test
    void testResetBucket() throws Exception {
        RateLimit config = getDummyRateLimitConfig();
        String clientKey = "192.168.1.101";

        assertTrue(rateLimitService.tryConsume(config.name(), clientKey, config));
        assertTrue(rateLimitService.tryConsume(config.name(), clientKey, config));
        assertFalse(rateLimitService.tryConsume(config.name(), clientKey, config));

        // Reset and try again
        rateLimitService.reset(config.name(), clientKey);
        assertTrue(rateLimitService.tryConsume(config.name(), clientKey, config));
    }
}
