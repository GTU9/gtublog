package com.gtublog.auth;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
class AuthRateLimiter {

    private final RateLimiter loginLimiter = RateLimiter.of(
            "auth-login",
            RateLimiterConfig.custom()
                    .limitForPeriod(10)
                    .limitRefreshPeriod(Duration.ofMinutes(1))
                    .timeoutDuration(Duration.ZERO)
                    .build());

    private final RateLimiter refreshLimiter = RateLimiter.of(
            "auth-refresh",
            RateLimiterConfig.custom()
                    .limitForPeriod(30)
                    .limitRefreshPeriod(Duration.ofMinutes(1))
                    .timeoutDuration(Duration.ZERO)
                    .build());

    void checkLogin() {
        if (!loginLimiter.acquirePermission()) {
            throw new RateLimitExceededException("Too many login attempts.");
        }
    }

    void checkRefresh() {
        if (!refreshLimiter.acquirePermission()) {
            throw new RateLimitExceededException("Too many refresh attempts.");
        }
    }
}
