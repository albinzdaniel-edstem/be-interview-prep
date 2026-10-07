package com.edstem.app.product.exception;

import com.edstem.app.common.exception.BaseException;
import java.util.UUID;

public class ProductNotFoundException extends BaseException {

  public ProductNotFoundException(UUID id) {
    super(ProductErrorCode.PRODUCT_NOT_FOUND, "Product not found: " + id);
  }
}
