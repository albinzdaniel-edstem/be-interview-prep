package com.edstem.app.product.exception;

import com.edstem.app.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ProductErrorCode implements ErrorCode {
  PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND),
  INVALID_PRICE_RANGE(HttpStatus.BAD_REQUEST),
  INSUFFICIENT_STOCK(HttpStatus.CONFLICT);

  private final HttpStatus status;

  @Override
  public String getCode() {
    return name();
  }
}
