package com.edstem.app.order.mapper;

import com.edstem.app.order.dto.response.OrderItemResponse;
import com.edstem.app.order.dto.response.OrderResponse;
import com.edstem.app.order.entity.OrderItem;
import com.edstem.app.order.entity.PurchaseOrder;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class OrderMapper {

  public OrderResponse toResponse(PurchaseOrder order) {
    List<OrderItemResponse> items =
        order.getItems().stream()
            .sorted(Comparator.comparing(OrderItem::getProductId))
            .map(item -> new OrderItemResponse(item.getProductId(), item.getQuantity()))
            .toList();
    return new OrderResponse(order.getId(), order.getStatus(), items, order.getCreatedAt());
  }
}
