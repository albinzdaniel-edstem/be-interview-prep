package com.edstem.app.product.exception;

import com.edstem.app.common.exception.BaseException;

public class InvalidPriceRangeException extends BaseException {

  public InvalidPriceRangeException() {
    super(
        ProductErrorCode.INVALID_PRICE_RANGE,
        "minPriceCents must not be greater than maxPriceCents");
  }
}
