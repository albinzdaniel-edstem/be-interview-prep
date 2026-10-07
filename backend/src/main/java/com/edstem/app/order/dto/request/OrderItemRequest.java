package com.edstem.app.order.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record OrderItemRequest(
    @NotNull(message = "Product id is required") UUID productId,
    @NotNull(message = "Quantity is required")
        @Min(value = 1, message = "Quantity must be at least 1")
        @Max(value = 1000, message = "Quantity must be at most 1000")
        Integer quantity) {}
