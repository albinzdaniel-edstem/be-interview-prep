package com.edstem.app.order.dto.response;

/** {@code created} is false when the request was a retry and the first order is returned. */
public record PlaceOrderResult(OrderResponse order, boolean created) {}
