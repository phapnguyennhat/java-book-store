package com.example.book_store.auth;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/** {@code app.auth.*}; startup fails if the JWT secret is missing. */
@ConfigurationProperties(prefix = "app.auth")
@Validated
record AuthProperties(
        @NotBlank String jwtSecret, // base64, >= 32 bytes: openssl rand -base64 48
        @NotBlank String appUrl, // frontend base URL, for links in emails
        @NotBlank String mailFrom,
        @DefaultValue("24h") Duration emailVerificationTtl,
        @DefaultValue("15m") Duration accessTokenTtl,
        @DefaultValue("7d") Duration refreshTokenTtl,
        @DefaultValue("true") boolean cookieSecure) {
}
