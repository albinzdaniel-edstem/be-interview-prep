package com.edstem.app.link.dto.response;

import java.time.Instant;

public record LinkResponse(
    String code, String shortUrl, String originalUrl, Instant expiresAt, Instant createdAt) {}
