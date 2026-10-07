package com.edstem.app.link.dto.response;

import java.time.Instant;

public record LinkStatsResponse(
    String code, String originalUrl, long visitCount, Instant createdAt, Instant expiresAt) {}
