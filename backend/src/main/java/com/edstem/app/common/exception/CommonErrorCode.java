package com.edstem.app.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CommonErrorCode implements ErrorCode {
  VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
  MALFORMED_REQUEST(HttpStatus.BAD_REQUEST),
  INVALID_PARAMETER(HttpStatus.BAD_REQUEST),
  REQUEST_REJECTED(HttpStatus.BAD_REQUEST),
  RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND),
  METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED),
  NOT_ACCEPTABLE(HttpStatus.NOT_ACCEPTABLE),
  UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE),
  INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

  private final HttpStatus status;

  @Override
  public String getCode() {
    return name();
  }
}
