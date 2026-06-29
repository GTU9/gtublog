package com.gtublog.auth;

public record AuthSessionResponse(
        String accessToken,
        String csrfToken,
        String tokenType,
        long expiresInSeconds,
        AdminProfileResponse admin) {
}
