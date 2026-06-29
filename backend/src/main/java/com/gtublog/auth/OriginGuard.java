package com.gtublog.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

@Component
class OriginGuard {

    private final AuthProperties authProperties;

    OriginGuard(AuthProperties authProperties) {
        this.authProperties = authProperties;
    }

    void validate(HttpServletRequest request) {
        var origin = request.getHeader("Origin");
        if (origin == null || authProperties.allowedOrigins().stream().noneMatch(origin::equals)) {
            throw AuthExceptions.invalidOrigin();
        }
    }
}
