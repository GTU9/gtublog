package com.gtublog.auth;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
class RefreshTokenCookieFactory {

    private static final String REFRESH_COOKIE = "gtublog_refresh";
    private static final String CSRF_COOKIE = "gtublog_csrf";
    private static final String COOKIE_PATH = "/api/v1/auth";

    private final AuthProperties authProperties;

    RefreshTokenCookieFactory(AuthProperties authProperties) {
        this.authProperties = authProperties;
    }

    ResponseCookie refreshCookie(String token) {
        var builder = ResponseCookie.from(REFRESH_COOKIE, token)
                .path(COOKIE_PATH)
                .httpOnly(true)
                .secure(authProperties.cookie().secure())
                .sameSite("Strict");
        if (hasDomain()) {
            builder.domain(authProperties.cookie().domain());
        }
        return builder.build();
    }

    ResponseCookie csrfCookie(String token) {
        var builder = ResponseCookie.from(CSRF_COOKIE, token)
                .path(COOKIE_PATH)
                .httpOnly(false)
                .secure(authProperties.cookie().secure())
                .sameSite("Strict");
        if (hasDomain()) {
            builder.domain(authProperties.cookie().domain());
        }
        return builder.build();
    }

    ResponseCookie clearRefreshCookie() {
        return clearCookie(REFRESH_COOKIE, true);
    }

    ResponseCookie clearCsrfCookie() {
        return clearCookie(CSRF_COOKIE, false);
    }

    private ResponseCookie clearCookie(String name, boolean httpOnly) {
        var builder = ResponseCookie.from(name, "")
                .path(COOKIE_PATH)
                .maxAge(0)
                .httpOnly(httpOnly)
                .secure(authProperties.cookie().secure())
                .sameSite("Strict");
        if (hasDomain()) {
            builder.domain(authProperties.cookie().domain());
        }
        return builder.build();
    }

    private boolean hasDomain() {
        return authProperties.cookie().domain() != null && !authProperties.cookie().domain().isBlank();
    }
}
