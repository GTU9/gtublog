package com.gtublog.auth;

record AuthSessionTokens(
        String accessToken,
        long expiresInSeconds,
        String refreshToken,
        String csrfToken,
        AdminUser adminUser) {

    AuthSessionResponse toResponse() {
        return new AuthSessionResponse(
                accessToken,
                csrfToken,
                "Bearer",
                expiresInSeconds,
                new AdminProfileResponse(adminUser.getId(), adminUser.getUsername(), adminUser.getDisplayName()));
    }
}
