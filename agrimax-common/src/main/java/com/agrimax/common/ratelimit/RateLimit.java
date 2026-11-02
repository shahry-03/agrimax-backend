package com.agrimax.common.ratelimit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Token-bucket rate limiter — per client IP.
 *
 * Example:
 *   @RateLimit(name = "login", capacity = 5, refillTokens = 5, refillPeriod = 15)
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    String name();
    long capacity();
    long refillTokens() default 0;   // 0 = use capacity
    long refillPeriod() default 1;
    RefillUnit refillUnit() default RefillUnit.MINUTES;

    enum RefillUnit { SECONDS, MINUTES, HOURS }
}