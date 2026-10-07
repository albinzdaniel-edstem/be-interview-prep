package com.edstem.app.product.exception;

import com.edstem.app.common.exception.BaseException;

public class InsufficientStockException extends BaseException {

  public InsufficientStockException(String productName, int requested, int available) {
    super(
        ProductErrorCode.INSUFFICIENT_STOCK,
        "Not enough stock for '%s': requested %d, available %d"
            .formatted(productName, requested, available));
  }
}
