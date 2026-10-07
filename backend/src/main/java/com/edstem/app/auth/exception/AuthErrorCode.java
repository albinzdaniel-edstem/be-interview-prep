package com.edstem.app.auth.exception;

import com.edstem.app.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements ErrorCode {
  TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED),
  INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED),
  EMAIL_ALREADY_REGISTERED(HttpStatus.CONFLICT),
  USER_NOT_FOUND(HttpStatus.NOT_FOUND);

  private final HttpStatus status;

  @Override
  public String getCode() {
    return name();
  }
}
