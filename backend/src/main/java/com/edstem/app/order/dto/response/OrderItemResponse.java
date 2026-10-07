package com.edstem.app.order.dto.response;

import java.util.UUID;

public record OrderItemResponse(UUID productId, int quantity) {}
