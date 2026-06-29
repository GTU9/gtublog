package com.gtublog.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.springframework.stereotype.Component;

@Component
class TokenHashingService {

    String sha256(String rawValue) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            var bytes = digest.digest(rawValue.getBytes(StandardCharsets.UTF_8));
            var builder = new StringBuilder(bytes.length * 2);
            for (var value : bytes) {
                builder.append(String.format("%02x", value));
            }
            return builder.toString();
        }
        catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm is unavailable.", exception);
        }
    }
}
