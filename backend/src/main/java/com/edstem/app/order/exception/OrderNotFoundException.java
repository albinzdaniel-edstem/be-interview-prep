package com.edstem.app.order.exception;

import com.edstem.app.common.exception.BaseException;
import java.util.UUID;

public class OrderNotFoundException extends BaseException {

  public OrderNotFoundException(UUID id) {
    super(OrderErrorCode.ORDER_NOT_FOUND, "Order not found: " + id);
  }
}
