package com.edstem.app.product.dto.response;

import java.time.Instant;
import java.util.UUID;

public record ProductResponse(
    UUID id,
    String name,
    String category,
    long priceCents,
    int stock,
    double rating,
    Instant createdAt) {}
