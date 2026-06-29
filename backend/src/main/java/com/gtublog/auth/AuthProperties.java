package com.gtublog.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(
        @NotBlank String issuer,
        @NotBlank String audience,
        @NotNull Duration accessTokenTtl,
        @NotNull Duration refreshTokenTtl,
        @NotEmpty List<String> allowedOrigins,
        @NotNull CookieProperties cookie,
        @NotNull BootstrapProperties bootstrap,
        @NotNull KeyProperties keys) {

    public record CookieProperties(boolean secure, String domain) {
    }

    public record BootstrapProperties(
            @NotBlank String username,
            @NotBlank String password,
            @NotBlank String displayName) {
    }

    public record KeyProperties(String publicKeyPem, String privateKeyPem) {
    }
}
