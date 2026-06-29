package com.gtublog.auth;

public record AdminProfileResponse(
        Long id,
        String username,
        String displayName) {
}
