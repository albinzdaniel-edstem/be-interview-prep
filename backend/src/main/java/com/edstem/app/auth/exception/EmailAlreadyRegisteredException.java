package com.edstem.app.auth.exception;

import com.edstem.app.common.exception.BaseException;

public class EmailAlreadyRegisteredException extends BaseException {

  public EmailAlreadyRegisteredException() {
    super(AuthErrorCode.EMAIL_ALREADY_REGISTERED, "An account with this email already exists");
  }
}
