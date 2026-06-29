package com.gtublog.auth;

import com.gtublog.audit.AuditActorType;
import com.gtublog.audit.AuditService;
import com.gtublog.audit.AuditTargetType;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final AdminUserRepository adminUserRepository;
    private final RefreshTokenFamilyRepository refreshTokenFamilyRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final TokenGenerator tokenGenerator;
    private final TokenHashingService tokenHashingService;
    private final AuditService auditService;
    private final AuthProperties authProperties;
    private final AuthRateLimiter authRateLimiter;
    private final Clock clock;

    public AuthService(
            AdminUserRepository adminUserRepository,
            RefreshTokenFamilyRepository refreshTokenFamilyRepository,
            RefreshTokenRepository refreshTokenRepository,
            AuthenticationManager authenticationManager,
            JwtService jwtService,
            TokenGenerator tokenGenerator,
            TokenHashingService tokenHashingService,
            AuditService auditService,
            AuthProperties authProperties,
            AuthRateLimiter authRateLimiter,
            Clock clock) {
        this.adminUserRepository = adminUserRepository;
        this.refreshTokenFamilyRepository = refreshTokenFamilyRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.tokenGenerator = tokenGenerator;
        this.tokenHashingService = tokenHashingService;
        this.auditService = auditService;
        this.authProperties = authProperties;
        this.authRateLimiter = authRateLimiter;
        this.clock = clock;
    }

    @Transactional
    public AuthSessionTokens login(LoginRequest request) {
        authRateLimiter.checkLogin();
        authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(request.username(), request.password()));
        var adminUser = adminUserRepository.findByUsername(request.username())
                .filter(AdminUser::isActive)
                .orElseThrow(AuthExceptions::invalidCredentials);
        var now = now();
        adminUser.markLoggedIn(now);
        auditService.record(
                AuditActorType.ADMIN,
                adminUser.getId().toString(),
                AuditTargetType.AUTH,
                adminUser.getId().toString(),
                "AUTH_LOGIN_SUCCESS",
                Map.of("username", adminUser.getUsername()));
        return issueSession(adminUser, null, now);
    }

    @Transactional(noRollbackFor = {BadCredentialsException.class, AccessDeniedException.class})
    public AuthSessionTokens refresh(String rawRefreshToken, String csrfToken) {
        authRateLimiter.checkRefresh();
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw AuthExceptions.invalidRefreshToken();
        }
        if (csrfToken == null || csrfToken.isBlank()) {
            throw AuthExceptions.invalidCsrf();
        }

        var now = now();
        var refreshToken = refreshTokenRepository.findWithLockByTokenHash(tokenHashingService.sha256(rawRefreshToken))
                .orElseThrow(AuthExceptions::invalidRefreshToken);
        var family = refreshTokenFamilyRepository.findWithLockById(refreshToken.getFamilyId())
                .orElseThrow(AuthExceptions::invalidRefreshToken);

        if (!family.isActiveAt(now) || !refreshToken.isUsableAt(now)) {
            family.revoke(now, "refresh-token-invalid");
            refreshToken.markReuseDetected(now);
            recordRefreshFailure(family, "AUTH_REFRESH_REPLAY_DETECTED");
            throw AuthExceptions.invalidRefreshToken();
        }

        if (!tokenHashingService.sha256(csrfToken).equals(family.getCsrfTokenHash())) {
            recordRefreshFailure(family, "AUTH_REFRESH_CSRF_REJECTED");
            throw AuthExceptions.invalidCsrf();
        }

        var adminUser = adminUserRepository.findById(family.getAdminUserId())
                .filter(AdminUser::isActive)
                .orElseThrow(AuthExceptions::invalidCredentials);

        refreshToken.markRotated(now);
        return issueSession(adminUser, refreshToken, now);
    }

    @Transactional(noRollbackFor = {BadCredentialsException.class, AccessDeniedException.class})
    public void logout(String rawRefreshToken, String csrfToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        if (csrfToken == null || csrfToken.isBlank()) {
            throw AuthExceptions.invalidCsrf();
        }
        var now = now();
        var refreshToken = refreshTokenRepository.findWithLockByTokenHash(tokenHashingService.sha256(rawRefreshToken))
                .orElseThrow(AuthExceptions::invalidRefreshToken);
        var family = refreshTokenFamilyRepository.findWithLockById(refreshToken.getFamilyId())
                .orElseThrow(AuthExceptions::invalidRefreshToken);
        if (!tokenHashingService.sha256(csrfToken).equals(family.getCsrfTokenHash())) {
            throw AuthExceptions.invalidCsrf();
        }
        family.revoke(now, "logout");
        refreshToken.markRevoked(now);
        auditService.record(
                AuditActorType.ADMIN,
                family.getAdminUserId().toString(),
                AuditTargetType.AUTH,
                family.getFamilyKey(),
                "AUTH_LOGOUT",
                Map.of("familyKey", family.getFamilyKey()));
    }

    @Transactional(readOnly = true)
    public AdminProfileResponse session(Jwt jwt) {
        var adminUserId = Long.parseLong(jwt.getSubject());
        var adminUser = adminUserRepository.findById(adminUserId).orElseThrow(AuthExceptions::invalidCredentials);
        return new AdminProfileResponse(adminUser.getId(), adminUser.getUsername(), adminUser.getDisplayName());
    }

    private AuthSessionTokens issueSession(AdminUser adminUser, RefreshToken predecessorToken, LocalDateTime now) {
        var refreshTokenValue = tokenGenerator.opaqueToken();
        var csrfTokenValue = tokenGenerator.opaqueToken();
        var family = predecessorToken == null
                ? refreshTokenFamilyRepository.save(RefreshTokenFamily.create(
                        tokenGenerator.key(),
                        adminUser.getId(),
                        now.plus(authProperties.refreshTokenTtl()),
                        tokenHashingService.sha256(csrfTokenValue)))
                : refreshTokenFamilyRepository.findWithLockById(predecessorToken.getFamilyId())
                        .map(existingFamily -> {
                            existingFamily.rotateCsrfToken(tokenHashingService.sha256(csrfTokenValue));
                            return existingFamily;
                        })
                        .orElseThrow(AuthExceptions::invalidRefreshToken);

        var refreshToken = RefreshToken.issue(
                tokenGenerator.key(),
                family.getId(),
                predecessorToken == null ? null : predecessorToken.getId(),
                tokenHashingService.sha256(refreshTokenValue),
                now.plus(authProperties.refreshTokenTtl()));
        refreshTokenRepository.save(refreshToken);

        var accessToken = jwtService.issueAccessToken(adminUser);
        var expiresAt = Instant.now(clock).plus(authProperties.accessTokenTtl());
        var expiresInSeconds = expiresAt.getEpochSecond() - Instant.now(clock).getEpochSecond();

        if (predecessorToken == null) {
            auditService.record(
                    AuditActorType.ADMIN,
                    adminUser.getId().toString(),
                    AuditTargetType.AUTH,
                    family.getFamilyKey(),
                    "AUTH_REFRESH_ISSUED",
                    Map.of("familyKey", family.getFamilyKey()));
        }
        else {
            auditService.record(
                    AuditActorType.ADMIN,
                    adminUser.getId().toString(),
                    AuditTargetType.AUTH,
                    family.getFamilyKey(),
                    "AUTH_REFRESH_ROTATED",
                    Map.of("familyKey", family.getFamilyKey(), "previousTokenId", predecessorToken.getId()));
        }

        return new AuthSessionTokens(accessToken, expiresInSeconds, refreshTokenValue, csrfTokenValue, adminUser);
    }

    private void recordRefreshFailure(RefreshTokenFamily family, String actionType) {
        auditService.record(
                AuditActorType.ADMIN,
                family.getAdminUserId().toString(),
                AuditTargetType.AUTH,
                family.getFamilyKey(),
                actionType,
                Map.of("familyKey", family.getFamilyKey()));
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
