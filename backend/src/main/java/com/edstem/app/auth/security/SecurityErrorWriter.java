package com.edstem.app.auth.security;

import com.edstem.app.common.dto.response.ApiResponse;
import com.edstem.app.common.dto.response.ErrorDetail;
import com.edstem.app.common.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SecurityErrorWriter {

  private final ObjectMapper objectMapper;

  public void write(
      HttpServletRequest request, HttpServletResponse response, ErrorCode errorCode, String message)
      throws IOException {
    ErrorDetail error =
        new ErrorDetail(
            errorCode.getCode(),
            errorCode.getStatus().value(),
            Instant.now(),
            request.getRequestURI(),
            null);
    response.setStatus(errorCode.getStatus().value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");
    objectMapper.writeValue(response.getOutputStream(), ApiResponse.failure(message, error));
  }
}
