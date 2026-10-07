package com.edstem.app.auth.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.security.jwt")
public record JwtProperties(
    @NotBlank(message = "JWT_SECRET must be set")
        @Size(min = 32, message = "JWT_SECRET must be at least 32 characters")
        String secret,
    @NotBlank String issuer,
    @NotNull Duration ttl) {}
