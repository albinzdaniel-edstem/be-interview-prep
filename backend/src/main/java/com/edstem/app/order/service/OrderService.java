package com.edstem.app.order.service;

import com.edstem.app.order.dto.request.PlaceOrderRequest;
import com.edstem.app.order.dto.response.OrderResponse;
import com.edstem.app.order.dto.response.PlaceOrderResult;
import com.edstem.app.order.entity.OrderItem;
import com.edstem.app.order.entity.OrderStatus;
import com.edstem.app.order.entity.PurchaseOrder;
import com.edstem.app.order.exception.OrderNotFoundException;
import com.edstem.app.order.mapper.OrderMapper;
import com.edstem.app.order.repository.OrderRepository;
import com.edstem.app.product.service.ProductStockService;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

  private final OrderRepository orderRepository;
  private final OrderPlacer orderPlacer;
  private final ProductStockService productStockService;
  private final OrderMapper orderMapper;

  /**
   * A retry is recognised by the pair (user, Idempotency-Key). The client sends one key per order
   * and reuses it when it retries. If an order with that pair exists, it is returned and nothing is
   * reserved again.
   *
   * <p>This method is not transactional on purpose. When two identical requests arrive at the same
   * moment, the database lets one insert win and rejects the other with a unique-constraint error.
   * That error rolls back the loser's transaction, so it must be caught outside it. The loser then
   * returns the winner's order.
   */
  public PlaceOrderResult place(UUID userId, String idempotencyKey, PlaceOrderRequest request) {
    Optional<PurchaseOrder> existing =
        orderRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
    if (existing.isPresent()) {
      return new PlaceOrderResult(orderMapper.toResponse(existing.get()), false);
    }
    try {
      return new PlaceOrderResult(orderPlacer.place(userId, idempotencyKey, request), true);
    } catch (DataIntegrityViolationException e) {
      return orderRepository
          .findByUserIdAndIdempotencyKey(userId, idempotencyKey)
          .map(order -> new PlaceOrderResult(orderMapper.toResponse(order), false))
          .orElseThrow(() -> e);
    }
  }

  /**
   * Cancels the caller's order and gives its stock back. The status change is one conditional
   * statement, so two cancel requests at the same moment return the stock only once. Cancelling an
   * order that is already cancelled changes nothing and returns it.
   */
  @Transactional
  public OrderResponse cancel(UUID userId, UUID orderId) {
    PurchaseOrder order =
        orderRepository
            .findByIdAndUserId(orderId, userId)
            .orElseThrow(() -> new OrderNotFoundException(orderId));

    int changed = orderRepository.changeStatus(orderId, OrderStatus.PLACED, OrderStatus.CANCELLED);
    if (changed == 1) {
      order.getItems().stream()
          .sorted(Comparator.comparing(OrderItem::getProductId))
          .forEach(item -> productStockService.release(item.getProductId(), item.getQuantity()));
      log.info("Cancelled order {}", orderId);
    }

    return orderRepository
        .findByIdAndUserId(orderId, userId)
        .map(orderMapper::toResponse)
        .orElseThrow(() -> new OrderNotFoundException(orderId));
  }
}
