package com.gtublog.auth;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class TokenGenerator {

    private final SecureRandom secureRandom = new SecureRandom();

    String opaqueToken() {
        var bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    String key() {
        return UUID.randomUUID().toString();
    }
}
