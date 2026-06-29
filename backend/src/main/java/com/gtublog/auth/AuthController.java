package com.gtublog.auth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenCookieFactory refreshTokenCookieFactory;
    private final OriginGuard originGuard;

    public AuthController(
            AuthService authService,
            RefreshTokenCookieFactory refreshTokenCookieFactory,
            OriginGuard originGuard) {
        this.authService = authService;
        this.refreshTokenCookieFactory = refreshTokenCookieFactory;
        this.originGuard = originGuard;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthSessionResponse> login(@Valid @RequestBody LoginRequest request) {
        var session = authService.login(request);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookieFactory.refreshCookie(session.refreshToken()).toString())
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookieFactory.csrfCookie(session.csrfToken()).toString())
                .body(session.toResponse());
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthSessionResponse> refresh(
            HttpServletRequest request,
            @CookieValue(name = "gtublog_refresh", required = false) String refreshToken,
            @RequestHeader(name = "X-CSRF-Token", required = false) String csrfToken) {
        originGuard.validate(request);
        var session = authService.refresh(refreshToken, csrfToken);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookieFactory.refreshCookie(session.refreshToken()).toString())
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookieFactory.csrfCookie(session.csrfToken()).toString())
                .body(session.toResponse());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            HttpServletRequest request,
            @CookieValue(name = "gtublog_refresh", required = false) String refreshToken,
            @RequestHeader(name = "X-CSRF-Token", required = false) String csrfToken) {
        originGuard.validate(request);
        authService.logout(refreshToken, csrfToken);
        return ResponseEntity.status(HttpStatus.NO_CONTENT)
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookieFactory.clearRefreshCookie().toString())
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookieFactory.clearCsrfCookie().toString())
                .build();
    }

    @GetMapping("/session")
    public AdminProfileResponse session(@AuthenticationPrincipal Jwt jwt) {
        return authService.session(jwt);
    }
}
