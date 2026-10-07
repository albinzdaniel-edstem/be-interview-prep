package com.edstem.app.order.exception;

import com.edstem.app.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum OrderErrorCode implements ErrorCode {
  ORDER_NOT_FOUND(HttpStatus.NOT_FOUND);

  private final HttpStatus status;

  @Override
  public String getCode() {
    return name();
  }
}
