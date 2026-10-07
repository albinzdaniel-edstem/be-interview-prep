package com.edstem.app.link.dto.request;

import com.edstem.app.link.validation.HttpUrl;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record ShortenUrlRequest(
    @NotBlank(message = "URL is required")
        @Size(max = 2048, message = "URL must be at most 2048 characters")
        @HttpUrl
        String url,
    @Future(message = "Expiry must be in the future") Instant expiresAt) {}
