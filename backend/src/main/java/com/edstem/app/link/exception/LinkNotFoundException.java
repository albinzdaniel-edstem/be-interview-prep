package com.edstem.app.link.exception;

import com.edstem.app.common.exception.BaseException;

public class LinkNotFoundException extends BaseException {

  public LinkNotFoundException(String code) {
    super(LinkErrorCode.LINK_NOT_FOUND, "Short link not found: " + code);
  }
}
