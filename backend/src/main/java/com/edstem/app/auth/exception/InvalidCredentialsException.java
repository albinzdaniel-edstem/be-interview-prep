package com.edstem.app.auth.exception;

import com.edstem.app.common.exception.BaseException;

public class InvalidCredentialsException extends BaseException {

  public InvalidCredentialsException() {
    super(AuthErrorCode.INVALID_CREDENTIALS, "Email or password is incorrect");
  }
}
