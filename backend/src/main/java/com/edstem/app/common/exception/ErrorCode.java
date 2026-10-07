package com.edstem.app.common.exception;

import org.springframework.http.HttpStatus;

public interface ErrorCode {

  String getCode();

  HttpStatus getStatus();
}
