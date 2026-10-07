package com.edstem.app.link.exception;

import com.edstem.app.common.exception.BaseException;

public class LinkExpiredException extends BaseException {

  public LinkExpiredException(String code) {
    super(LinkErrorCode.LINK_EXPIRED, "Short link has expired: " + code);
  }
}
