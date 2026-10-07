package com.edstem.app.common.exception;

import lombok.Getter;

@Getter
public abstract class BaseException extends RuntimeException {

  private final ErrorCode errorCode;

  protected BaseException(ErrorCode errorCode, String message) {
    super(message);
    this.errorCode = errorCode;
  }
}
