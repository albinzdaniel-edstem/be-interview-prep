package com.edstem.app.auth.exception;

import com.edstem.app.common.exception.BaseException;

public class UserNotFoundException extends BaseException {

  public UserNotFoundException() {
    super(AuthErrorCode.USER_NOT_FOUND, "User not found");
  }
}
