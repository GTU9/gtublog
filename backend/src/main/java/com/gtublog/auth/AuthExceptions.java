package com.gtublog.auth;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

final class AuthExceptions {

    private AuthExceptions() {
    }

    static BadCredentialsException invalidCredentials() {
        return new BadCredentialsException("Invalid credentials.");
    }

    static BadCredentialsException invalidRefreshToken() {
        return new BadCredentialsException("Invalid refresh token.");
    }

    static AccessDeniedException invalidCsrf() {
        return new AccessDeniedException("Invalid CSRF token.");
    }

    static AccessDeniedException invalidOrigin() {
        return new AccessDeniedException("Invalid origin.");
    }
}
