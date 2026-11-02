package com.agrimax.common.ratelimit;

import com.agrimax.common.exception.RateLimitExceededException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class RateLimitAspect {

    private final RateLimitService rateLimitService;

    @Value("${agrimax.rate-limit.enabled:true}")
    private boolean rateLimitEnabled;

    @Around("@annotation(rateLimit)")
    public Object applyRateLimit(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        if (!rateLimitEnabled) {
            return joinPoint.proceed();
        }

        String clientKey = resolveClientKey();
        boolean allowed = rateLimitService.tryConsume(rateLimit.name(), clientKey, rateLimit);

        if (!allowed) {
            throw new RateLimitExceededException(
                    "Too many requests. Please try again later.",
                    rateLimit.refillPeriod(),
                    rateLimit.refillUnit());
        }

        return joinPoint.proceed();
    }

    private String resolveClientKey() {
        var attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) return "unknown";

        HttpServletRequest request = attrs.getRequest();

        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) return realIp;

        return request.getRemoteAddr();
    }
}