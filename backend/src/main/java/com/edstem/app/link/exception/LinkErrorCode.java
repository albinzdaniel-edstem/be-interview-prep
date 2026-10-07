package com.edstem.app.link.exception;

import com.edstem.app.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum LinkErrorCode implements ErrorCode {
  LINK_NOT_FOUND(HttpStatus.NOT_FOUND),
  LINK_EXPIRED(HttpStatus.GONE),
  SHORT_CODE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE);

  private final HttpStatus status;

  @Override
  public String getCode() {
    return name();
  }
}
