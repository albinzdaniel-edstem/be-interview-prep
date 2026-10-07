package com.edstem.app.order.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record PlaceOrderRequest(
    @NotEmpty(message = "At least one item is required")
        @Size(max = 50, message = "An order can have at most 50 items")
        List<@Valid OrderItemRequest> items) {}
