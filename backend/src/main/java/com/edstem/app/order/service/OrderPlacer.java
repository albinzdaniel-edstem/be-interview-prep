package com.edstem.app.order.service;

import com.edstem.app.order.dto.request.PlaceOrderRequest;
import com.edstem.app.order.dto.response.OrderResponse;
import com.edstem.app.order.entity.OrderStatus;
import com.edstem.app.order.entity.PurchaseOrder;
import com.edstem.app.order.mapper.OrderMapper;
import com.edstem.app.order.repository.OrderRepository;
import com.edstem.app.product.service.ProductStockService;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Places an order in one transaction: the order row and every stock change are committed together
 * or not at all.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderPlacer {

  private final OrderRepository orderRepository;
  private final ProductStockService productStockService;
  private final OrderMapper orderMapper;

  @Transactional
  public OrderResponse place(UUID userId, String idempotencyKey, PlaceOrderRequest request) {
    Map<UUID, Integer> quantities = totalPerProduct(request);
    PurchaseOrder order =
        PurchaseOrder.builder()
            .userId(userId)
            .idempotencyKey(idempotencyKey)
            .status(OrderStatus.PLACED)
            .build();
    quantities.forEach(order::addItem);

    // The unique (user, key) constraint is checked here, before any stock changes. A retry that
    // arrives at the same moment as the first request fails here and takes nothing from the stock.
    PurchaseOrder saved = orderRepository.saveAndFlush(order);

    // Any item without enough stock throws, and the whole transaction is rolled back, including the
    // items already reserved above it.
    quantities.forEach(productStockService::reserve);

    log.info("Placed order {}", saved.getId());
    return orderMapper.toResponse(saved);
  }

  /**
   * Adds up lines for the same product and sorts them by product id. Every order then locks product
   * rows in the same order, so two orders with the same products listed differently cannot wait on
   * each other forever (a deadlock).
   */
  private static Map<UUID, Integer> totalPerProduct(PlaceOrderRequest request) {
    Map<UUID, Integer> totals = new TreeMap<>();
    request.items().forEach(item -> totals.merge(item.productId(), item.quantity(), Integer::sum));
    return totals;
  }
}
