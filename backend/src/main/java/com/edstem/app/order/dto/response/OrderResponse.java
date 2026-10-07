package com.edstem.app.order.dto.response;

import com.edstem.app.order.entity.OrderStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
    UUID id, OrderStatus status, List<OrderItemResponse> items, Instant createdAt) {}
