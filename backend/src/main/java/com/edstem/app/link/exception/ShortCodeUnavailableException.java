package com.edstem.app.link.exception;

import com.edstem.app.common.exception.BaseException;

public class ShortCodeUnavailableException extends BaseException {

  public ShortCodeUnavailableException() {
    super(
        LinkErrorCode.SHORT_CODE_UNAVAILABLE,
        "Could not create a short link right now. Please try again.");
  }
}
