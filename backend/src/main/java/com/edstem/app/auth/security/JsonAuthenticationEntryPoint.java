package com.edstem.app.auth.security;

import com.edstem.app.auth.exception.AuthErrorCode;
import com.edstem.app.common.exception.CommonErrorCode;
import com.edstem.app.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

  private final SecurityErrorWriter errorWriter;

  @Override
  public void commence(
      HttpServletRequest request,
      HttpServletResponse response,
      AuthenticationException authException)
      throws IOException {
    boolean expired =
        authException.getMessage() != null && authException.getMessage().contains("expired");
    ErrorCode errorCode = expired ? AuthErrorCode.TOKEN_EXPIRED : CommonErrorCode.UNAUTHENTICATED;
    String message =
        expired
            ? "Your login has expired. Please log in again."
            : "Authentication is required to access this resource";
    response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
    errorWriter.write(request, response, errorCode, message);
  }
}
